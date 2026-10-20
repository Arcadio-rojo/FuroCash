package com.furocash.dao;

import com.furocash.model.OtpRecord;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

public class OtpDao {

  // Also serializes requests when a code row does not exist yet.
  public void lock(Connection conn, String mobile, String purpose)
    throws SQLException {
    try (
      PreparedStatement ps = conn.prepareStatement(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))"
      )
    ) {
      ps.setString(1, "furocash:otp:" + purpose + ":" + mobile);
      ps.execute();
    }
  }

  public Optional<OtpRecord> find(
    Connection conn,
    String mobile,
    String purpose
  ) throws SQLException {
    try (
      PreparedStatement ps = conn.prepareStatement(
        "SELECT code_hash, expires_at, attempts, last_sent_at, consumed FROM otp_codes WHERE mobile_number = ? AND purpose = ? FOR UPDATE"
      )
    ) {
      ps.setString(1, mobile);
      ps.setString(2, purpose);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(
          new OtpRecord(
            mobile,
            rs.getString("code_hash"),
            rs.getTimestamp("expires_at").toLocalDateTime(),
            rs.getInt("attempts"),
            rs.getTimestamp("last_sent_at").toLocalDateTime(),
            rs.getBoolean("consumed")
          )
        );
      }
    }
  }

  public void upsert(
    Connection conn,
    String mobile,
    String purpose,
    String hash,
    LocalDateTime expiry,
    LocalDateTime sent
  ) throws SQLException {
    String sql =
      "INSERT INTO otp_codes (mobile_number,purpose,code_hash,expires_at,attempts,last_sent_at,consumed) VALUES (?,?,?,?,0,?,false) ON CONFLICT (mobile_number,purpose) DO UPDATE SET code_hash=EXCLUDED.code_hash,expires_at=EXCLUDED.expires_at,attempts=0,last_sent_at=EXCLUDED.last_sent_at,consumed=false";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, mobile);
      ps.setString(2, purpose);
      ps.setString(3, hash);
      ps.setTimestamp(4, Timestamp.valueOf(expiry));
      ps.setTimestamp(5, Timestamp.valueOf(sent));
      ps.executeUpdate();
    }
  }

  public void incrementAttempts(Connection conn, String mobile, String purpose)
    throws SQLException {
    update(conn, "attempts=attempts+1", mobile, purpose);
  }

  public void consume(Connection conn, String mobile, String purpose)
    throws SQLException {
    update(conn, "consumed=true", mobile, purpose);
  }

  private void update(
    Connection conn,
    String assignment,
    String mobile,
    String purpose
  ) throws SQLException {
    try (
      PreparedStatement ps = conn.prepareStatement(
        "UPDATE otp_codes SET " +
          assignment +
          " WHERE mobile_number=? AND purpose=?"
      )
    ) {
      ps.setString(1, mobile);
      ps.setString(2, purpose);
      ps.executeUpdate();
    }
  }
}
