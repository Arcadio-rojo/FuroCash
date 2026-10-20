package com.furocash.service;

import com.furocash.dao.UserDao;
import com.furocash.exception.*;
import com.furocash.model.User;
import com.furocash.util.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;

public class AuthService {

  private final UserDao userDao = new UserDao();

  static void validateRegistration(String mobile, String pin, String fullName) {
    validateCredentials(mobile, pin);
    if (
      !InputValidator.isValidFullName(fullName)
    ) throw new InvalidInputException(
      "Full name is required (max 100 characters)"
    );
  }

  static void validateCredentials(String mobile, String pin) {
    if (!InputValidator.isValidMobile(mobile)) throw new InvalidInputException(
      "Mobile number must be 11 digits starting with 09"
    );
    if (!InputValidator.isValidPin(pin)) throw new InvalidInputException(
      "PIN must be exactly 4 digits"
    );
  }

  User register(Connection conn, String mobile, String pin, String fullName)
    throws SQLException {
    validateRegistration(mobile, pin, fullName);
    if (
      userDao.findByMobile(conn, mobile).isPresent()
    ) throw new UserAlreadyExistsException(
      "Mobile number is already registered"
    );
    User user = new User();
    user.setMobileNumber(mobile);
    user.setPinHash(PinHasher.hash(pin));
    user.setFullName(fullName.trim());
    user.setBalance(BigDecimal.ZERO);
    try {
      user.setId(userDao.insert(conn, user));
    } catch (SQLException e) {
      if ("23505".equals(e.getSQLState())) throw new UserAlreadyExistsException(
        "Mobile number is already registered"
      );
      throw e;
    }
    return user;
  }

  public User currentUser(long id) {
    try (Connection conn = DBConnection.getConnection()) {
      return userDao
        .findById(conn, id)
        .orElseThrow(() ->
          new AuthenticationRequiredException(
            "Your account is unavailable. Please log in again"
          )
        );
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  public User login(String mobile, String pin) {
    validateCredentials(mobile, pin);
    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      try {
        User user = userDao
          .findByMobileForUpdate(conn, mobile)
          .orElseThrow(() ->
            new InvalidCredentialsException("Invalid mobile number or PIN")
          );
        LocalDateTime now = LocalDateTime.now();
        if (
          user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)
        ) {
          long minutes =
            Duration.between(now, user.getLockedUntil()).toMinutes() + 1;
          throw new AccountLockedException(
            "Account is locked. Try again in " + minutes + " minute(s)"
          );
        }
        if (user.getLockedUntil() != null) {
          user.setFailedAttempts(0);
          user.setLockedUntil(null);
        }
        if (!PinHasher.matches(pin, user.getPinHash())) {
          int attempts = user.getFailedAttempts() + 1;
          userDao.updateLoginAttempts(
            conn,
            user.getId(),
            attempts,
            attempts >= 3 ? now.plusMinutes(5) : null
          );
          conn.commit(); // Persist the failed attempt before reporting it.
          if (attempts >= 3) throw new AccountLockedException(
            "Too many failed attempts. Account locked for 5 minutes"
          );
          throw new InvalidCredentialsException("Invalid mobile number or PIN");
        }
        userDao.updateLoginAttempts(conn, user.getId(), 0, null);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        conn.commit();
        return user;
      } catch (SQLException | RuntimeException e) {
        conn.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }
}
