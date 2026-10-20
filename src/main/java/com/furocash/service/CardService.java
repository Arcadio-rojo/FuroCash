package com.furocash.service;

import com.furocash.dao.UserDao;
import com.furocash.dto.CardPurchaseRequest;
import com.furocash.dto.CardResponse;
import com.furocash.dto.Receipt;
import com.furocash.dto.TransactionResponse;
import com.furocash.exception.FuroCashException;
import com.furocash.exception.InsufficientFundsException;
import com.furocash.exception.InvalidInputException;
import com.furocash.exception.UserNotFoundException;
import com.furocash.util.BankingSupport;
import com.furocash.util.DBConnection;
import com.furocash.util.ReferenceNumber;
import io.javalin.http.ConflictResponse;
import io.javalin.http.NotFoundResponse;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal demo cards spend the existing account balance; no external card network is used. */
public final class CardService {
  private final UserDao users = new UserDao();
  private final IdempotencyService idempotency = new IdempotencyService();

  public CardResponse create(long userId) {
    UUID id = UUID.randomUUID();
    String identifier = "DEMO-FC-" + id.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    try (Connection conn = DBConnection.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO demo_cards(id,user_id,identifier,status,daily_limit,expires_on) " +
        "VALUES (?,?,?,'ACTIVE',5000,(CURRENT_DATE + INTERVAL '3 years')::date)"
      )) {
        ps.setObject(1, id);
        ps.setLong(2, userId);
        ps.setString(3, identifier);
        ps.executeUpdate();
      }
      return owned(conn, userId, id, false);
    } catch (SQLException e) {
      if ("23505".equals(e.getSQLState())) throw new ConflictResponse("A demo card already exists for this account");
      throw new FuroCashException("Database error", e);
    }
  }

  public CardResponse mine(long userId) {
    try (Connection conn = DBConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(
      "SELECT id FROM demo_cards WHERE user_id=?"
    )) {
      ps.setLong(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? owned(conn, userId, rs.getObject("id", UUID.class), false) : null;
      }
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  public CardResponse setFrozen(long userId, UUID cardId, boolean frozen) {
    return change(userId, cardId, frozen ? "FROZEN" : "ACTIVE", null);
  }

  public CardResponse setLimit(long userId, UUID cardId, BigDecimal limit) {
    BankingSupport.validAmount(limit);
    return change(userId, cardId, null, limit);
  }

  private CardResponse change(long userId, UUID cardId, String status, BigDecimal limit) {
    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      try {
        owned(conn, userId, cardId, true);
        try (PreparedStatement ps = conn.prepareStatement(status == null
          ? "UPDATE demo_cards SET daily_limit=? WHERE id=? AND user_id=?"
          : "UPDATE demo_cards SET status=? WHERE id=? AND user_id=?")) {
          if (status == null) ps.setBigDecimal(1, limit);
          else ps.setString(1, status);
          ps.setObject(2, cardId);
          ps.setLong(3, userId);
          ps.executeUpdate();
        }
        CardResponse result = owned(conn, userId, cardId, false);
        conn.commit();
        return result;
      } catch (SQLException | RuntimeException e) {
        conn.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  public Receipt purchase(long userId, UUID cardId, CardPurchaseRequest request, String key) {
    if (request == null) throw new InvalidInputException("Purchase details are required");
    BankingSupport.validAmount(request.amount());
    String merchant = request.merchant() == null ? "" : request.merchant().trim();
    if (merchant.isBlank() || merchant.length() > 100 || merchant.codePoints().anyMatch(Character::isISOControl)) {
      throw new InvalidInputException("Merchant is required (max 100 characters)");
    }
    return idempotency.execute(userId, key, "CARD_PURCHASE", Map.of(
      "cardId", cardId.toString(), "amount", request.amount(), "merchant", request.merchant()
    ), conn -> {
      // Lock the card before the account. Read daily spending in a separate statement after
      // acquiring the card lock so concurrent waiters see the previous purchase's commit.
      CardResponse card = owned(conn, userId, cardId, true);
      if (!"ACTIVE".equals(card.status())) throw new ConflictResponse("Unfreeze your demo card before making a purchase");
      var user = users.findByIdForUpdate(conn, userId)
        .orElseThrow(() -> new UserNotFoundException("User not found"));
      if (card.spentToday().add(request.amount()).compareTo(card.dailyLimit()) > 0) {
        throw new InsufficientFundsException("Daily card spending limit exceeded");
      }
      if (user.getBalance().compareTo(request.amount()) < 0) {
        throw new InsufficientFundsException("Insufficient account balance");
      }
      BigDecimal balance = user.getBalance().subtract(request.amount());
      users.updateBalance(conn, userId, balance);
      String reference = ReferenceNumber.generate();
      String createdAt;
      try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO transactions(user_id,type,amount,details,reference_no,balance_after,status,card_id) " +
        "VALUES (?,'CARD_PURCHASE',?,?,?,?,'COMPLETED',?) RETURNING created_at"
      )) {
        ps.setLong(1, userId);
        ps.setBigDecimal(2, request.amount());
        ps.setString(3, "Demo card purchase: " + merchant);
        ps.setString(4, reference);
        ps.setBigDecimal(5, balance);
        ps.setObject(6, cardId);
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) throw new SQLException("Purchase history was not created");
          createdAt = rs.getTimestamp("created_at").toLocalDateTime().toString();
        }
      }
      return new Receipt(reference, "CARD_PURCHASE", "COMPLETED", request.amount(), balance,
        BankingSupport.accountIdentifier(userId), merchant, null, createdAt);
    });
  }

  public List<TransactionResponse> transactions(long userId, UUID cardId) {
    try (Connection conn = DBConnection.getConnection()) {
      owned(conn, userId, cardId, false);
      List<TransactionResponse> result = new ArrayList<>();
      try (PreparedStatement ps = conn.prepareStatement(
        "SELECT id,type,amount,details,reference_no,balance_after,created_at,status,card_id " +
        "FROM transactions WHERE user_id=? AND card_id=? ORDER BY created_at DESC,id DESC"
      )) {
        ps.setLong(1, userId);
        ps.setObject(2, cardId);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            result.add(new TransactionResponse(rs.getLong("id"), rs.getString("type"),
              rs.getBigDecimal("amount"), rs.getString("details"), rs.getString("reference_no"),
              rs.getBigDecimal("balance_after"), rs.getTimestamp("created_at").toLocalDateTime().toString(),
              rs.getString("status"), rs.getObject("card_id").toString()));
          }
        }
      }
      return result;
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  private CardResponse owned(Connection conn, long userId, UUID cardId, boolean lock) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT c.*,u.full_name FROM demo_cards c JOIN users u ON u.id=c.user_id " +
      "WHERE c.id=? AND c.user_id=?" + (lock ? " FOR UPDATE OF c" : "")
    )) {
      ps.setObject(1, cardId);
      ps.setLong(2, userId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) throw new NotFoundResponse("Demo card not found");
        String identifier = rs.getString("identifier");
        return new CardResponse(cardId.toString(), identifier,
          "DEMO-FC-****-" + identifier.substring(identifier.length() - 4), rs.getString("full_name"),
          rs.getString("status"), rs.getBigDecimal("daily_limit"), spentToday(conn, userId, cardId),
          rs.getDate("expires_on").toLocalDate().toString(), rs.getTimestamp("created_at").toLocalDateTime().toString());
      }
    }
  }

  private BigDecimal spentToday(Connection conn, long userId, UUID cardId) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE user_id=? AND card_id=? " +
      "AND type='CARD_PURCHASE' AND status='COMPLETED' " +
      "AND created_at>=CURRENT_DATE AND created_at<CURRENT_DATE + INTERVAL '1 day'"
    )) {
      ps.setLong(1, userId);
      ps.setObject(2, cardId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getBigDecimal(1);
      }
    }
  }
}
