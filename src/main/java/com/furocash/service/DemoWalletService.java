package com.furocash.service;

import com.furocash.dao.*;
import com.furocash.dto.*;
import com.furocash.exception.*;
import com.furocash.model.*;
import com.furocash.util.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** Internal simulated money only. This service never calls an external payment network. */
public final class DemoWalletService {

  private final UserDao users = new UserDao();
  private final TransactionDao transactions = new TransactionDao();
  private final IdempotencyService operations = new IdempotencyService();

  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private static final Map<String, String> METHODS = Map.of(
    "GCASH_DEMO",
    "GCash (Demo)",
    "CASH_DEMO",
    "Cash Deposit (Demo)",
    "BANK_TRANSFER_DEMO",
    "Bank Transfer (Demo)",
    "EWALLET_DEMO",
    "E-Wallet (Demo)",
    "OTHER_DEMO",
    "Other (Demo)"
  );

  public Map<String, Object> depositOptions() {
    return Map.of(
      "enabled",
      AppConfig.enabled("DEMO_CASH_IN"),
      "minAmount",
      new BigDecimal("0.01"),
      "manualMaxAmount",
      manualMax(),
      "fee",
      ZERO,
      "methods",
      List.of("CASH_DEMO", "BANK_TRANSFER_DEMO", "EWALLET_DEMO", "OTHER_DEMO")
        .stream()
        .map(m -> Map.of("value", m, "label", METHODS.get(m)))
        .toList()
    );
  }

  private static BigDecimal manualMax() {
    try {
      BigDecimal value = new BigDecimal(
        AppConfig.get("DEMO_MANUAL_DEPOSIT_MAX", "50000")
      );
      BankingSupport.validAmount(value);
      return value;
    } catch (NumberFormatException e) {
      throw new InvalidInputException(
        "Invalid manual deposit maximum configuration"
      );
    }
  }

  public Receipt prepareDeposit(
    long userId,
    BigDecimal amount,
    String method,
    String key
  ) {
    return prepareDeposit(userId, amount, method, null, key);
  }

  public Receipt prepareDeposit(
    long userId,
    BigDecimal amount,
    String method,
    String rawNote,
    String key
  ) {
    fundingEnabled();
    BankingSupport.validAmount(amount);
    if (
      method == null || !METHODS.containsKey(method)
    ) throw new InvalidInputException("Choose a supported demo deposit method");
    if (
      !"GCASH_DEMO".equals(method) && amount.compareTo(manualMax()) > 0
    ) throw new InvalidAmountException(
      "Amount exceeds the manual deposit maximum"
    );
    String note = validatedNote(rawNote, 150, "Deposit");
    Map<String, Object> payload = new HashMap<>(
      Map.of("amount", decimal(amount), "method", method)
    );
    if (!note.isEmpty()) payload.put("note", note);
    return operations.execute(userId, key, "DEPOSIT_PREPARE", payload, conn -> {
      User owner = user(conn, userId, false);
      String reference = "DEP-" + UUID.randomUUID();
      String created;
      try (
        var ps = conn.prepareStatement(
          "INSERT INTO demo_deposits(reference,user_id,method,amount,note) VALUES (?,?,?,?,?) RETURNING created_at"
        )
      ) {
        ps.setString(1, reference);
        ps.setLong(2, userId);
        ps.setString(3, method);
        ps.setBigDecimal(4, amount);
        ps.setString(5, note);
        try (var rs = ps.executeQuery()) {
          rs.next();
          created = rs.getTimestamp(1).toLocalDateTime().toString();
        }
      }
      return new Receipt(
        reference,
        "DEPOSIT",
        "PENDING",
        amount,
        owner.getBalance(),
        BankingSupport.accountIdentifier(userId),
        METHODS.get(method),
        note,
        created,
        ZERO,
        amount,
        METHODS.getOrDefault(method, "Demo Cash-In"),
        null,
        null,
        BankingSupport.accountIdentifier(userId),
        owner.getFullName()
      );
    });
  }

  public Receipt completeDeposit(long userId, String reference, String key) {
    fundingEnabled();
    if (
      reference == null || reference.length() > 64
    ) throw new InvalidInputException("Invalid deposit reference");
    return operations.execute(
      userId,
      key,
      "DEPOSIT_COMPLETE",
      Map.of("reference", reference),
      conn -> {
        BigDecimal amount;
        String method, note;
        try (
          var ps = conn.prepareStatement(
            "SELECT amount,method,note,status,completion_response::text FROM demo_deposits WHERE reference=? AND user_id=? FOR UPDATE"
          )
        ) {
          ps.setString(1, reference);
          ps.setLong(2, userId);
          try (var rs = ps.executeQuery()) {
            if (!rs.next()) throw new UserNotFoundException(
              "Deposit request not found"
            );
            if (
              "COMPLETED".equals(rs.getString("status"))
            ) return IdempotencyService.parseReceipt(
              rs.getString("completion_response")
            );
            amount = rs.getBigDecimal("amount");
            method = rs.getString("method");
            note = rs.getString("note");
          }
        }
        User owner = user(conn, userId, true);
        BigDecimal balance = owner.getBalance().add(amount);
        users.updateBalance(conn, userId, balance);
        Transaction tx = addTransaction(
          conn,
          userId,
          TransactionType.CASH_IN,
          amount,
          METHODS.getOrDefault(method, "Demo deposit") + " - simulated funds",
          reference,
          balance
        );
        Receipt result = new Receipt(
          reference,
          "DEPOSIT",
          "COMPLETED",
          amount,
          balance,
          BankingSupport.accountIdentifier(userId),
          METHODS.getOrDefault(method, "Demo deposit"),
          note,
          tx.getCreatedAt().toString(),
          ZERO,
          amount,
          METHODS.getOrDefault(method, "Demo Cash-In"),
          null,
          null,
          BankingSupport.accountIdentifier(userId),
          owner.getFullName()
        );
        persistReceipt(conn, userId, tx, result);
        try (
          var ps = conn.prepareStatement(
            "UPDATE demo_deposits SET status='COMPLETED',completed_at=NOW(),completion_response=?::jsonb WHERE reference=?"
          )
        ) {
          ps.setString(1, IdempotencyService.json(result));
          ps.setString(2, reference);
          ps.executeUpdate();
        }
        return result;
      }
    );
  }

  public Receipt legacyCashIn(long userId, BigDecimal amount, String key) {
    fundingEnabled();
    BankingSupport.validAmount(amount);
    return operations.execute(
      userId,
      key,
      "CASH_IN",
      Map.of("amount", decimal(amount)),
      conn -> {
        User owner = user(conn, userId, true);
        BigDecimal balance = owner.getBalance().add(amount);
        users.updateBalance(conn, userId, balance);
        String ref = ReferenceNumber.generate();
        Transaction tx = addTransaction(
          conn,
          userId,
          TransactionType.CASH_IN,
          amount,
          "Demo cash-in",
          ref,
          balance
        );
        Receipt result = new Receipt(
          ref,
          "DEPOSIT",
          "COMPLETED",
          amount,
          balance,
          BankingSupport.accountIdentifier(userId),
          "Demo cash-in",
          "Simulated funds",
          tx.getCreatedAt().toString(),
          ZERO,
          amount,
          "Demo Cash-In",
          null,
          null,
          BankingSupport.accountIdentifier(userId),
          owner.getFullName()
        );
        persistReceipt(conn, userId, tx, result);
        return result;
      }
    );
  }

  public RecipientResponse validateRecipient(
    long userId,
    String account,
    String mobile
  ) {
    try (var conn = DBConnection.getConnection()) {
      User receiver = recipientUser(conn, account, mobile);
      if (receiver.getId() == userId) throw new InvalidInputException(
        "You cannot transfer money to yourself"
      );
      return recipientResponse(receiver);
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  public record TransferReview(
    String reference,
    RecipientResponse recipient,
    BigDecimal amount,
    BigDecimal fee,
    BigDecimal total,
    BigDecimal balance,
    String note,
    String qrReference,
    String expiresAt
  ) {}

  public TransferReview reviewTransfer(long senderId, TransferRequest request) {
    if (request == null) throw new InvalidInputException(
      "Transfer details are required"
    );
    BankingSupport.validAmount(request.amount());
    String note = transferNote(request.note()),
      qrReference = clean(request.qrReference());
    try (var conn = DBConnection.getConnection()) {
      User receiver = recipientUser(
        conn,
        request.receiverAccount(),
        request.receiverMobile()
      );
      if (receiver.getId() == senderId) throw new InvalidInputException(
        "You cannot transfer money to yourself"
      );
      User sender = user(conn, senderId, false);
      if (!qrReference.isEmpty()) validateQr(
        conn,
        qrReference,
        receiver.getId(),
        request.amount(),
        false
      );
      if (
        sender.getBalance().compareTo(request.amount()) < 0
      ) throw new InsufficientFundsException("Insufficient balance");
      String reference = "REV-" + UUID.randomUUID();
      String expires;
      try (
        var ps = conn.prepareStatement(
          "INSERT INTO transfer_reviews(reference,user_id,recipient_id,recipient_name,amount,note,qr_reference) VALUES (?,?,?,?,?,?,?) RETURNING expires_at"
        )
      ) {
        ps.setString(1, reference);
        ps.setLong(2, senderId);
        ps.setLong(3, receiver.getId());
        ps.setString(4, receiver.getFullName());
        ps.setBigDecimal(5, request.amount());
        ps.setString(6, note);
        ps.setString(7, qrReference);
        try (var rs = ps.executeQuery()) {
          rs.next();
          expires = rs.getTimestamp(1).toLocalDateTime().toString();
        }
      }
      return new TransferReview(
        reference,
        recipientResponse(receiver),
        request.amount(),
        ZERO,
        request.amount(),
        sender.getBalance(),
        note,
        qrReference,
        expires
      );
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private static String validatedNote(
    String value,
    int maxLength,
    String kind
  ) {
    if (
      value != null && value.codePoints().anyMatch(Character::isISOControl)
    ) throw new InvalidInputException(
      kind + " note cannot contain control characters"
    );
    String note = clean(value);
    if (note.length() > maxLength) throw new InvalidInputException(
      kind + " note must be at most " + maxLength + " characters"
    );
    return note;
  }

  private static String transferNote(String value) {
    return validatedNote(value, 140, "Transfer");
  }

  private QrRecord validateQr(
    Connection conn,
    String reference,
    long receiverId,
    BigDecimal amount,
    boolean lock
  ) throws SQLException {
    QrRecord qr = readQr(conn, reference, lock);
    checkActive(qr);
    if (
      !"TRANSFER".equals(qr.purpose()) || qr.userId() != receiverId
    ) throw new InvalidInputException(
      "This QR request does not match the transfer recipient"
    );
    if (
      qr.amount() != null && qr.amount().compareTo(amount) != 0
    ) throw new InvalidInputException("The amount must match this QR request");
    return qr;
  }

  private void checkReview(
    Connection conn,
    long senderId,
    TransferRequest request,
    User receiver,
    String note,
    String qrReference
  ) throws SQLException {
    if (request.reviewReference() == null) return;
    try (
      var ps = conn.prepareStatement(
        "SELECT recipient_id,recipient_name,amount,fee,note,qr_reference,consumed_at,expires_at>NOW() AS active FROM transfer_reviews WHERE reference=? AND user_id=? FOR UPDATE"
      )
    ) {
      ps.setString(1, request.reviewReference());
      ps.setLong(2, senderId);
      try (var rs = ps.executeQuery()) {
        if (
          !rs.next() ||
          !rs.getBoolean("active") ||
          rs.getTimestamp("consumed_at") != null ||
          rs.getLong("recipient_id") != receiver.getId() ||
          !rs.getString("recipient_name").equals(receiver.getFullName()) ||
          rs.getBigDecimal("amount").compareTo(request.amount()) != 0 ||
          rs.getBigDecimal("fee").compareTo(ZERO) != 0 ||
          !rs.getString("note").equals(note) ||
          !rs.getString("qr_reference").equals(qrReference)
        ) throw new OperationConflictException(
          "Transfer review changed or expired. Review the transfer again"
        );
      }
    }
  }

  public Receipt transactionReceipt(long userId, String reference) {
    try (
      var conn = DBConnection.getConnection();
      var ps = conn.prepareStatement(
        "SELECT receipt::text FROM transaction_receipts WHERE user_id=? AND reference=?"
      )
    ) {
      ps.setLong(1, userId);
      ps.setString(2, reference);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) throw new UserNotFoundException(
          "Receipt not available"
        );
        return IdempotencyService.parseReceipt(rs.getString(1));
      }
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  public IdempotencyService.OperationStatus operationStatus(
    long userId,
    String key
  ) {
    return operations.status(userId, key);
  }

  private void persistReceipt(
    Connection conn,
    long ownerId,
    Transaction tx,
    Receipt receipt
  ) throws SQLException {
    if (!"COMPLETED".equals(receipt.status())) throw new IllegalStateException(
      "Only completed receipts may be persisted"
    );
    try (
      var ps = conn.prepareStatement(
        "INSERT INTO transaction_receipts(user_id,reference,transaction_id,receipt) VALUES (?,?,?,?::jsonb)"
      )
    ) {
      ps.setLong(1, ownerId);
      ps.setString(2, receipt.reference());
      ps.setLong(3, tx.getId());
      ps.setString(4, IdempotencyService.json(receipt));
      ps.executeUpdate();
    }
  }

  public Receipt transfer(long senderId, TransferRequest request, String key) {
    if (request == null) throw new InvalidInputException(
      "Transfer details are required"
    );
    BankingSupport.validAmount(request.amount());
    String note = transferNote(request.note());
    RecipientResponse validated = validateRecipient(
      senderId,
      request.receiverAccount(),
      request.receiverMobile()
    );
    String qrReference =
      request.qrReference() == null ? "" : request.qrReference().trim();
    Map<String, Object> payload = new HashMap<>(
      Map.of(
        "recipient",
        validated.accountIdentifier(),
        "amount",
        decimal(request.amount()),
        "note",
        note,
        "qrReference",
        qrReference
      )
    );
    if (request.reviewReference() != null) payload.put(
      "reviewReference",
      request.reviewReference()
    );
    return operations.execute(senderId, key, "TRANSFER", payload, conn -> {
      User receiverLookup = recipientUser(
        conn,
        validated.accountIdentifier(),
        null
      );
      long receiverId = receiverLookup.getId();
      QrRecord qr = null;
      if (!qrReference.isEmpty()) {
        qr = readQr(conn, qrReference, true);
        checkActive(qr);
        if (
          !"TRANSFER".equals(qr.purpose()) || qr.userId() != receiverId
        ) throw new InvalidInputException(
          "This QR request does not match the transfer recipient"
        );
        if (
          qr.amount() != null && qr.amount().compareTo(request.amount()) != 0
        ) throw new InvalidInputException(
          "The amount must match this QR request"
        );
      }
      User first = user(conn, Math.min(senderId, receiverId), true),
        second = user(conn, Math.max(senderId, receiverId), true);
      User sender = first.getId() == senderId ? first : second,
        receiver = first.getId() == receiverId ? first : second;
      checkReview(conn, senderId, request, receiver, note, qrReference);
      if (
        sender.getBalance().compareTo(request.amount()) < 0
      ) throw new InsufficientFundsException("Insufficient balance");
      BigDecimal senderBalance = sender.getBalance().subtract(request.amount()),
        receiverBalance = receiver.getBalance().add(request.amount());
      users.updateBalance(conn, senderId, senderBalance);
      users.updateBalance(conn, receiverId, receiverBalance);
      String reference = ReferenceNumber.generate();
      String suffix = note.isEmpty() ? "" : " ? " + note;
      Transaction sent = addTransaction(
        conn,
        senderId,
        TransactionType.TRANSFER_OUT,
        request.amount(),
        "Sent to " + BankingSupport.accountIdentifier(receiverId) + suffix,
        reference,
        senderBalance
      );
      Transaction received = addTransaction(
        conn,
        receiverId,
        TransactionType.TRANSFER_IN,
        request.amount(),
        "Received from " + BankingSupport.accountIdentifier(senderId) + suffix,
        reference,
        receiverBalance
      );
      if (qr != null) try (
        var ps = conn.prepareStatement(
          "UPDATE qr_payment_requests SET consumed_at=NOW(),consumed_by=? WHERE reference=?"
        )
      ) {
        ps.setLong(1, senderId);
        ps.setObject(2, qr.reference());
        ps.executeUpdate();
      }
      if (request.reviewReference() != null) try (
        var ps = conn.prepareStatement(
          "UPDATE transfer_reviews SET consumed_at=NOW() WHERE reference=? AND user_id=?"
        )
      ) {
        ps.setString(1, request.reviewReference());
        ps.setLong(2, senderId);
        ps.executeUpdate();
      }
      Receipt result = new Receipt(
        reference,
        "TRANSFER",
        "COMPLETED",
        request.amount(),
        senderBalance,
        BankingSupport.accountIdentifier(senderId),
        receiver.getFullName(),
        note,
        sent.getCreatedAt().toString(),
        ZERO,
        request.amount(),
        "FuroCash Wallet Transfer (Demo)",
        BankingSupport.accountIdentifier(senderId),
        sender.getFullName(),
        BankingSupport.accountIdentifier(receiverId),
        receiver.getFullName()
      );
      Receipt incoming = new Receipt(
        reference,
        "TRANSFER_IN",
        "COMPLETED",
        request.amount(),
        receiverBalance,
        BankingSupport.accountIdentifier(receiverId),
        sender.getFullName(),
        note,
        received.getCreatedAt().toString(),
        ZERO,
        request.amount(),
        "FuroCash Wallet Transfer (Demo)",
        BankingSupport.accountIdentifier(senderId),
        sender.getFullName(),
        BankingSupport.accountIdentifier(receiverId),
        receiver.getFullName()
      );
      persistReceipt(conn, senderId, sent, result);
      persistReceipt(conn, receiverId, received, incoming);
      return result;
    });
  }

  public QrPayload createQr(long userId, String purpose, BigDecimal amount) {
    if (
      !Set.of("TRANSFER", "DEPOSIT").contains(purpose == null ? "" : purpose)
    ) throw new InvalidInputException("Choose a transfer or demo deposit QR");
    if (amount != null) BankingSupport.validAmount(amount);
    try (var conn = DBConnection.getConnection()) {
      user(conn, userId, false);
      UUID reference = UUID.randomUUID();
      try (
        var ps = conn.prepareStatement(
          "INSERT INTO qr_payment_requests(reference,user_id,purpose,amount,expires_at) VALUES (?,?,?,?,?)"
        )
      ) {
        ps.setObject(1, reference);
        ps.setLong(2, userId);
        ps.setString(3, purpose);
        ps.setBigDecimal(4, amount);
        ps.setTimestamp(
          5,
          Timestamp.valueOf(LocalDateTime.now().plusHours(24))
        );
        ps.executeUpdate();
      }
      return new QrPayload(
        "FUROCASH",
        1,
        purpose,
        BankingSupport.accountIdentifier(userId),
        reference.toString(),
        amount == null ? null : decimal(amount)
      );
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  public record QrResolution(
    RecipientResponse recipient,
    String amount,
    String reference,
    String purpose
  ) {}

  public QrResolution resolveQr(QrPayload payload) {
    if (
      payload == null ||
      !"FUROCASH".equals(payload.app()) ||
      payload.version() != 1 ||
      !Set.of("TRANSFER", "DEPOSIT").contains(
        payload.purpose() == null ? "" : payload.purpose()
      )
    ) throw new InvalidInputException(
      "Unsupported FuroCash QR code or version"
    );
    try (var conn = DBConnection.getConnection()) {
      QrRecord qr = readQr(conn, payload.reference(), false);
      checkActive(qr);
      if (
        !qr.purpose().equals(payload.purpose()) ||
        !BankingSupport.accountIdentifier(qr.userId()).equals(
          payload.accountIdentifier()
        )
      ) throw new InvalidInputException(
        "The QR details do not match the saved request"
      );
      BigDecimal supplied = null;
      if (payload.amount() != null) {
        try {
          supplied = new BigDecimal(payload.amount());
          BankingSupport.validAmount(supplied);
        } catch (NumberFormatException e) {
          throw new InvalidInputException("Invalid QR amount");
        }
      }
      if (
        (qr.amount() == null) != (supplied == null) ||
        (qr.amount() != null && qr.amount().compareTo(supplied) != 0)
      ) throw new InvalidInputException(
        "The QR amount does not match the saved request"
      );
      return new QrResolution(
        recipientResponse(user(conn, qr.userId(), false)),
        qr.amount() == null ? null : decimal(qr.amount()),
        qr.reference().toString(),
        qr.purpose()
      );
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  private record QrRecord(
    UUID reference,
    long userId,
    String purpose,
    BigDecimal amount,
    LocalDateTime expiresAt,
    Timestamp consumedAt
  ) {}

  private QrRecord readQr(Connection conn, String reference, boolean lock)
    throws SQLException {
    UUID id;
    try {
      id = UUID.fromString(reference);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidInputException("Invalid QR reference");
    }
    try (
      var ps = conn.prepareStatement(
        "SELECT reference,user_id,purpose,amount,expires_at,consumed_at FROM qr_payment_requests WHERE reference=?" +
          (lock ? " FOR UPDATE" : "")
      )
    ) {
      ps.setObject(1, id);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) throw new InvalidInputException(
          "This QR request was not issued by FuroCash"
        );
        return new QrRecord(
          id,
          rs.getLong("user_id"),
          rs.getString("purpose"),
          rs.getBigDecimal("amount"),
          rs.getTimestamp("expires_at").toLocalDateTime(),
          rs.getTimestamp("consumed_at")
        );
      }
    }
  }

  private void checkActive(QrRecord qr) {
    if (qr.consumedAt() != null) throw new OperationConflictException(
      "This QR request has already been paid"
    );
    if (
      !qr.expiresAt().isAfter(LocalDateTime.now())
    ) throw new InvalidInputException(
      "This QR request expired. Generate a new one"
    );
  }

  private User recipientUser(Connection conn, String account, String mobile)
    throws SQLException {
    User result;
    if (account != null && !account.isBlank()) {
      String identifier = account.trim().toUpperCase(Locale.ROOT);
      long id;
      try {
        if (
          !identifier.matches("FC-[0-9]{10,19}")
        ) throw new NumberFormatException();
        id = Long.parseLong(identifier.substring(3));
        if (
          id <= 0 || !BankingSupport.accountIdentifier(id).equals(identifier)
        ) throw new NumberFormatException();
      } catch (NumberFormatException e) {
        throw new InvalidInputException(
          "Enter a valid FuroCash account identifier"
        );
      }
      result = user(conn, id, false);
      if (
        mobile != null &&
        !mobile.isBlank() &&
        !result.getMobileNumber().equals(mobile)
      ) throw new InvalidInputException("Recipient details do not match");
    } else {
      if (
        !InputValidator.isValidMobile(mobile)
      ) throw new InvalidInputException(
        "Enter a FuroCash account identifier or registered mobile number"
      );
      result = users
        .findByMobile(conn, mobile)
        .orElseThrow(() -> new UserNotFoundException("Recipient not found"));
    }
    return result;
  }

  private User user(Connection conn, long id, boolean lock)
    throws SQLException {
    return (
      lock ? users.findByIdForUpdate(conn, id) : users.findById(conn, id)
    ).orElseThrow(() -> new UserNotFoundException("Account not found"));
  }

  private RecipientResponse recipientResponse(User user) {
    return new RecipientResponse(
      BankingSupport.accountIdentifier(user.getId()),
      user.getFullName(),
      user.getMobileNumber()
    );
  }

  private Transaction addTransaction(
    Connection conn,
    long userId,
    TransactionType type,
    BigDecimal amount,
    String details,
    String ref,
    BigDecimal balance
  ) throws SQLException {
    Transaction tx = new Transaction();
    tx.setUserId(userId);
    tx.setType(type);
    tx.setAmount(amount);
    tx.setDetails(details);
    tx.setReferenceNo(ref);
    tx.setBalanceAfter(balance);
    transactions.insert(conn, tx);
    return tx;
  }

  private static String decimal(BigDecimal amount) {
    return amount.setScale(2).toPlainString();
  }

  private static void fundingEnabled() {
    if (
      !AppConfig.enabled("DEMO_CASH_IN")
    ) throw new io.javalin.http.ForbiddenResponse(
      "Demo deposits are disabled on this server"
    );
  }
}
