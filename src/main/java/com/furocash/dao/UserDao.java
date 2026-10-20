package com.furocash.dao;

import com.furocash.model.User;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

public class UserDao {

  private User mapRow(ResultSet rs) throws SQLException {
    User user = new User();
    user.setId(rs.getLong("id"));
    user.setSessionVersion(rs.getInt("session_version"));
    user.setMobileNumber(rs.getString("mobile_number"));
    user.setPinHash(rs.getString("pin_hash"));
    user.setFullName(rs.getString("full_name"));
    user.setEmail(rs.getString("email"));
    Timestamp createdAt = rs.getTimestamp("created_at");
    user.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
    user.setBalance(rs.getBigDecimal("balance"));
    user.setFailedAttempts(rs.getInt("failed_attempts"));

    Timestamp lockedUntilTs = rs.getTimestamp("locked_until");
    if (lockedUntilTs != null) {
      user.setLockedUntil(lockedUntilTs.toLocalDateTime());
    } else {
      user.setLockedUntil(null);
    }
    return user;
  }

  public Optional<User> findByMobile(Connection conn, String mobile)
    throws SQLException {
    String sql =
      "SELECT id, mobile_number, pin_hash, full_name, email, created_at, balance, failed_attempts, locked_until, session_version FROM users WHERE mobile_number = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, mobile);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    }
    return Optional.empty();
  }

  public Optional<User> findById(Connection conn, long id) throws SQLException {
    String sql =
      "SELECT id, mobile_number, pin_hash, full_name, email, created_at, balance, failed_attempts, locked_until, session_version FROM users WHERE id = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    }
    return Optional.empty();
  }

  public Optional<User> findByIdForUpdate(Connection conn, long id)
    throws SQLException {
    String sql =
      "SELECT id, mobile_number, pin_hash, full_name, email, created_at, balance, failed_attempts, locked_until, session_version " +
      "FROM users WHERE id = ? FOR UPDATE";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    }
    return Optional.empty();
  }

  public long insert(Connection conn, User user) throws SQLException {
    String sql =
      "INSERT INTO users (mobile_number, pin_hash, full_name, balance) VALUES (?, ?, ?, ?) RETURNING id";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, user.getMobileNumber());
      ps.setString(2, user.getPinHash());
      ps.setString(3, user.getFullName());
      ps.setBigDecimal(4, user.getBalance());

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getLong("id");
        }
      }
    }
    throw new SQLException("Failed to insert user, no ID obtained.");
  }

  public void updateBalance(Connection conn, long userId, BigDecimal newBalance)
    throws SQLException {
    String sql = "UPDATE users SET balance = ? WHERE id = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setBigDecimal(1, newBalance);
      ps.setLong(2, userId);
      ps.executeUpdate();
    }
  }

  public void updateProfile(Connection conn, long userId, String fullName, String email)
    throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
      "UPDATE users SET full_name = ?, email = ? WHERE id = ?"
    )) {
      ps.setString(1, fullName);
      ps.setString(2, email);
      ps.setLong(3, userId);
      ps.executeUpdate();
    }
  }

  public void updateLoginAttempts(
    Connection conn,
    long userId,
    int failedAttempts,
    LocalDateTime lockedUntil
  ) throws SQLException {
    String sql =
      "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, failedAttempts);
      if (lockedUntil != null) {
        ps.setTimestamp(2, Timestamp.valueOf(lockedUntil));
      } else {
        ps.setTimestamp(2, null);
      }
      ps.setLong(3, userId);
      ps.executeUpdate();
    }
  }

  public Optional<User> findByMobileForUpdate(Connection conn, String mobile)
    throws SQLException {
    try (
      PreparedStatement ps = conn.prepareStatement(
        "SELECT * FROM users WHERE mobile_number=? FOR UPDATE"
      )
    ) {
      ps.setString(1, mobile);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
      }
    }
  }

  public void updatePin(Connection conn, long userId, String hash)
    throws SQLException {
    try (
      PreparedStatement ps = conn.prepareStatement(
        "UPDATE users SET pin_hash=?,session_version=session_version+1,failed_attempts=0,locked_until=NULL WHERE id=?"
      )
    ) {
      ps.setString(1, hash);
      ps.setLong(2, userId);
      ps.executeUpdate();
    }
  }
}
