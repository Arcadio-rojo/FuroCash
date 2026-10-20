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

  public Receipt prepareDeposit(
    long userId,
    BigDecimal amount,
    String method,
    String key
  ) {
    fundingEnabled();
    BankingSupport.validAmount(amount);
    if (!"GCASH_DEMO".equals(method)) throw new InvalidInputException(
      "Select the demo GCash deposit method"
    );
    return operations.execute(
      userId,
      key,
      "DEPOSIT_PREPARE",
      Map.of("amount", decimal(amount), "method", method),
      conn -> {
        User user = user(conn, userId, false);
        String reference = "DEP-" + UUID.randomUUID();
        try (
          var ps = conn.prepareStatement(
            "INSERT INTO demo_deposits(reference,user_id,method,amount) VALUES (?,?,?,?)"
          )
        ) {
          ps.setString(1, reference);
          ps.setLong(2, userId);
          ps.setString(3, method);
          ps.setBigDecimal(4, amount);
          ps.executeUpdate();
        }
        return receipt(
          reference,
          "DEPOSIT",
          "PENDING",
          amount,
          user.getBalance(),
          userId,
          "Demo GCash",
          "No real GCash payment",
          LocalDateTime.now().toString()
        );
      }
    );
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
        try (
          var ps = conn.prepareStatement(
            "SELECT amount,status,completion_response::text FROM demo_deposits WHERE reference=? AND user_id=? FOR UPDATE"
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
          }
        }
        User user = user(conn, userId, true);
        BigDecimal balance = user.getBalance().add(amount);
        users.updateBalance(conn, userId, balance);
        addTransaction(
          conn,
          userId,
          TransactionType.CASH_IN,
          amount,
          "Demo GCash deposit ? simulated funds",
          reference,
          balance
        );
        Receipt result = receipt(
          reference,
          "DEPOSIT",
          "COMPLETED",
          amount,
          balance,
          userId,
          "Demo GCash",
          "No real GCash payment",
          LocalDateTime.now().toString()
        );
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
        User user = user(conn, userId, true);
        BigDecimal balance = user.getBalance().add(amount);
        users.updateBalance(conn, userId, balance);
        String ref = ReferenceNumber.generate();
        addTransaction(
          conn,
          userId,
          TransactionType.CASH_IN,
          amount,
          "Demo cash-in",
          ref,
          balance
        );
        return receipt(
          ref,
          "DEPOSIT",
          "COMPLETED",
          amount,
          balance,
          userId,
          "Demo cash-in",
          "Simulated funds",
          LocalDateTime.now().toString()
        );
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

  public Receipt transfer(long senderId, TransferRequest request, String key) {
    if (request == null) throw new InvalidInputException(
      "Transfer details are required"
    );
    BankingSupport.validAmount(request.amount());
    String note = request.note() == null ? "" : request.note().trim();
    if (note.length() > 140) throw new InvalidInputException(
      "Transfer note must be at most 140 characters"
    );
    RecipientResponse validated = validateRecipient(
      senderId,
      request.receiverAccount(),
      request.receiverMobile()
    );
    String qrReference =
      request.qrReference() == null ? "" : request.qrReference().trim();
    return operations.execute(
      senderId,
      key,
      "TRANSFER",
      Map.of(
        "recipient",
        validated.accountIdentifier(),
        "amount",
        decimal(request.amount()),
        "note",
        note,
        "qrReference",
        qrReference
      ),
      conn -> {
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
        if (
          sender.getBalance().compareTo(request.amount()) < 0
        ) throw new InsufficientFundsException("Insufficient balance");
        BigDecimal senderBalance = sender
            .getBalance()
            .subtract(request.amount()),
          receiverBalance = receiver.getBalance().add(request.amount());
        users.updateBalance(conn, senderId, senderBalance);
        users.updateBalance(conn, receiverId, receiverBalance);
        String reference = ReferenceNumber.generate();
        String suffix = note.isEmpty() ? "" : " ? " + note;
        addTransaction(
          conn,
          senderId,
          TransactionType.TRANSFER_OUT,
          request.amount(),
          "Sent to " + BankingSupport.accountIdentifier(receiverId) + suffix,
          reference,
          senderBalance
        );
        addTransaction(
          conn,
          receiverId,
          TransactionType.TRANSFER_IN,
          request.amount(),
          "Received from " +
            BankingSupport.accountIdentifier(senderId) +
            suffix,
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
        return receipt(
          reference,
          "TRANSFER",
          "COMPLETED",
          request.amount(),
          senderBalance,
          senderId,
          receiver.getFullName(),
          note,
          LocalDateTime.now().toString()
        );
      }
    );
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

  private void addTransaction(
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
  }

  private static Receipt receipt(
    String ref,
    String kind,
    String status,
    BigDecimal amount,
    BigDecimal balance,
    long userId,
    String counterparty,
    String note,
    String date
  ) {
    return new Receipt(
      ref,
      kind,
      status,
      amount,
      balance,
      BankingSupport.accountIdentifier(userId),
      counterparty,
      note,
      date
    );
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
