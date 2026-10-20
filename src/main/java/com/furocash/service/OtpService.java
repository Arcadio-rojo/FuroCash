package com.furocash.service;

import com.furocash.dao.*;
import com.furocash.exception.*;
import com.furocash.model.*;
import com.furocash.sms.SmsSender;
import com.furocash.util.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.Locale;

public class OtpService {

  private static final String REGISTER = "REGISTER",
    RESET = "RESET_PIN";
  private final OtpDao otpDao = new OtpDao();
  private final UserDao userDao = new UserDao();
  private final SmsSender smsSender;
  private final SecureRandom random = new SecureRandom();

  public OtpService(SmsSender sender) {
    smsSender = sender;
  }

  public void requestRegistrationCode(
    String mobile,
    String pin,
    String fullName
  ) {
    AuthService.validateRegistration(mobile, pin, fullName);
    request(mobile, REGISTER);
  }

  public void requestResetCode(String mobile) {
    if (!InputValidator.isValidMobile(mobile)) throw new InvalidInputException(
      "Mobile number must be 11 digits starting with 09"
    );
    request(mobile, RESET);
  }

  private void request(String mobile, String purpose) {
    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      try {
        otpDao.lock(conn, mobile, purpose);
        boolean registered = userDao.findByMobile(conn, mobile).isPresent();
        if (
          REGISTER.equals(purpose) && registered
        ) throw new UserAlreadyExistsException(
          "Mobile number is already registered"
        );
        if (RESET.equals(purpose) && !registered) {
          conn.commit();
          return;
        }
        LocalDateTime now = LocalDateTime.now();
        var existing = otpDao.find(conn, mobile, purpose);
        if (existing.isPresent()) {
          long seconds =
            60 -
            Duration.between(existing.get().lastSentAt(), now).getSeconds();
          if (seconds > 0) throw new TooManyRequestsException(
            "Please wait " + seconds + " second(s) before requesting a new code"
          );
        }
        String code = String.format(
          Locale.ROOT,
          "%06d",
          random.nextInt(1_000_000)
        );
        otpDao.upsert(
          conn,
          mobile,
          purpose,
          PinHasher.hash(code),
          now.plusMinutes(5),
          now
        );
        smsSender.send(
          mobile,
          "Your FuroCash " +
            (REGISTER.equals(purpose) ? "registration" : "PIN reset") +
            " code is " +
            code +
            ". It expires in 5 minutes. Do not share it."
        );
        conn.commit();
      } catch (SQLException | RuntimeException e) {
        conn.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }

  public User register(
    String mobile,
    String pin,
    String fullName,
    String code
  ) {
    AuthService.validateRegistration(mobile, pin, fullName);
    return complete(mobile, REGISTER, code, conn ->
      new AuthService().register(conn, mobile, pin, fullName)
    );
  }

  public void resetPin(String mobile, String code, String pin) {
    AuthService.validateCredentials(mobile, pin);
    complete(mobile, RESET, code, conn -> {
      User user = userDao
        .findByMobileForUpdate(conn, mobile)
        .orElseThrow(() ->
          new InvalidOtpException("Invalid recovery code. Request a new one")
        );
      userDao.updatePin(conn, user.getId(), PinHasher.hash(pin));
      return null;
    });
  }

  @FunctionalInterface
  private interface VerifiedAction<T> {
    T run(Connection conn) throws SQLException;
  }

  private <T> T complete(
    String mobile,
    String purpose,
    String code,
    VerifiedAction<T> action
  ) {
    if (
      code == null || !code.matches("[0-9]{6}")
    ) throw new InvalidOtpException("Enter the 6-digit code");
    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      try {
        otpDao.lock(conn, mobile, purpose);
        OtpRecord record = otpDao
          .find(conn, mobile, purpose)
          .orElseThrow(() ->
            new InvalidOtpException("No active code. Request a new one")
          );
        if (
          record.consumed() || record.attempts() >= 3
        ) throw new InvalidOtpException(
          "This code is no longer active. Request a new one"
        );
        if (!record.expiresAt().isAfter(LocalDateTime.now())) {
          otpDao.consume(conn, mobile, purpose);
          throw new InvalidOtpException("The code expired. Request a new one");
        }
        if (!PinHasher.matches(code, record.codeHash())) {
          otpDao.incrementAttempts(conn, mobile, purpose);
          throw new InvalidOtpException(
            record.attempts() + 1 >= 3
              ? "Too many wrong codes. Request a new one"
              : "Wrong code. " + (2 - record.attempts()) + " attempt(s) left"
          );
        }
        T result = action.run(conn);
        otpDao.consume(conn, mobile, purpose);
        conn.commit();
        return result;
      } catch (InvalidOtpException e) {
        conn.commit();
        throw e;
      } catch (SQLException | RuntimeException e) {
        conn.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }
}
