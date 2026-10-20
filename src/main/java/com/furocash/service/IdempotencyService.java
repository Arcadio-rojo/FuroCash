package com.furocash.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.furocash.dto.Receipt;
import com.furocash.exception.*;
import com.furocash.util.DBConnection;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.HexFormat;

/** Runs the effect and records its result atomically. Duplicate keys are serialized before any balance lock. */
public final class IdempotencyService {

  private static final ObjectMapper JSON = new ObjectMapper().enable(
    SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS
  );

  @FunctionalInterface
  public interface SqlWork<T> {
    T run(Connection conn) throws SQLException;
  }

  public Receipt execute(
    long userId,
    String key,
    String operation,
    Object payload,
    SqlWork<Receipt> work
  ) {
    if (
      key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,79}")
    ) throw new InvalidInputException(
      "A valid Idempotency-Key header is required (8?80 characters)"
    );
    String fingerprint = hash(json(payload));
    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      try {
        try (
          var lock = conn.prepareStatement(
            "SELECT pg_advisory_xact_lock(hashtextextended(?,0))"
          )
        ) {
          lock.setString(1, "furocash:idem:" + userId + ":" + key);
          lock.execute();
        }
        try (
          var query = conn.prepareStatement(
            "SELECT operation,request_hash,response::text FROM idempotency_operations WHERE user_id=? AND idempotency_key=?"
          )
        ) {
          query.setLong(1, userId);
          query.setString(2, key);
          try (var rs = query.executeQuery()) {
            if (rs.next()) {
              if (
                !operation.equals(rs.getString("operation")) ||
                !fingerprint.equals(rs.getString("request_hash"))
              ) throw new OperationConflictException(
                "This request key was already used for different details"
              );
              Receipt saved = parseReceipt(rs.getString("response"));
              conn.commit();
              return saved;
            }
          }
        }
        Receipt result = work.run(conn);
        try (
          var insert = conn.prepareStatement(
            "INSERT INTO idempotency_operations(user_id,idempotency_key,operation,request_hash,response) VALUES (?,?,?,?,?::jsonb)"
          )
        ) {
          insert.setLong(1, userId);
          insert.setString(2, key);
          insert.setString(3, operation);
          insert.setString(4, fingerprint);
          insert.setString(5, json(result));
          insert.executeUpdate();
        }
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

  public static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new FuroCashException("Could not encode the operation", e);
    }
  }

  public static Receipt parseReceipt(String value) {
    try {
      return JSON.readValue(value, Receipt.class);
    } catch (JsonProcessingException e) {
      throw new FuroCashException("Could not read the saved operation", e);
    }
  }

  private static String hash(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          value.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
