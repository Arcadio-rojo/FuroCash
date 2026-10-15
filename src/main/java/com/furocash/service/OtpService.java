package com.furocash.service;

import com.furocash.dao.OtpDao;
import com.furocash.dao.UserDao;
import com.furocash.exception.FuroCashException;
import com.furocash.exception.InvalidInputException;
import com.furocash.exception.InvalidOtpException;
import com.furocash.exception.SmsSendException;
import com.furocash.exception.TooManyRequestsException;
import com.furocash.exception.UserAlreadyExistsException;
import com.furocash.model.OtpRecord;
import com.furocash.sms.SmsSender;
import com.furocash.util.DBConnection;
import com.furocash.util.InputValidator;
import com.furocash.util.PinHasher;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public class OtpService {

    private static final int EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final int RESEND_SECONDS = 60;

    private final OtpDao otpDao = new OtpDao();
    private final UserDao userDao = new UserDao();
    private final SmsSender smsSender;
    private final SecureRandom random = new SecureRandom();

    public OtpService(SmsSender smsSender) {
        this.smsSender = smsSender;
    }

    public void requestRegistrationCode(String mobile, String pin, String fullName) {
        if (!InputValidator.isValidMobile(mobile)) {
            throw new InvalidInputException("Mobile number must be 11 digits starting with 09");
        }
        if (!InputValidator.isValidPin(pin)) {
            throw new InvalidInputException("PIN must be exactly 4 digits");
        }
        if (!InputValidator.isValidFullName(fullName)) {
            throw new InvalidInputException("Full name is required (max 100 characters)");
        }

        LocalDateTime now = LocalDateTime.now();
        String code = String.format("%06d", random.nextInt(1_000_000));

        try (Connection conn = DBConnection.getConnection()) {
            if (userDao.findByMobile(conn, mobile).isPresent()) {
                throw new UserAlreadyExistsException("Mobile number is already registered");
            }

            Optional<OtpRecord> existing = otpDao.findByMobile(conn, mobile);
            if (existing.isPresent()) {
                long wait = RESEND_SECONDS - Duration.between(existing.get().lastSentAt(), now).getSeconds();
                if (wait > 0) {
                    throw new TooManyRequestsException("Please wait " + wait + " second(s) before requesting a new code");
                }
            }

            otpDao.upsert(conn, mobile, PinHasher.hash(code), now.plusMinutes(EXPIRY_MINUTES), now);
        } catch (SQLException e) {
            throw new FuroCashException("Database error", e);
        }

        try {
            smsSender.send(mobile, "Your FuroCash code is " + code
                    + ". It expires in " + EXPIRY_MINUTES + " minutes. Do not share it.");
        } catch (SmsSendException e) {
            discard(mobile);   // so a failed send doesn't start the 60-second wait
            throw e;
        }
    }

    public void verify(String mobile, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new InvalidOtpException("Enter the 6-digit code");
        }

        try (Connection conn = DBConnection.getConnection()) {
            OtpRecord record = otpDao.findByMobile(conn, mobile)
                    .orElseThrow(() -> new InvalidOtpException("No code was requested for this number. Request a new one"));

            if (record.expiresAt().isBefore(LocalDateTime.now())) {
                otpDao.delete(conn, mobile);
                throw new InvalidOtpException("The code expired. Request a new one");
            }

            if (!PinHasher.matches(code, record.codeHash())) {
                int attempts = record.attempts() + 1;
                if (attempts >= MAX_ATTEMPTS) {
                    otpDao.delete(conn, mobile);
                    throw new InvalidOtpException("Too many wrong codes. Request a new one");
                }
                otpDao.incrementAttempts(conn, mobile);
                throw new InvalidOtpException("Wrong code. " + (MAX_ATTEMPTS - attempts) + " attempt(s) left");
            }

            otpDao.delete(conn, mobile);
        } catch (SQLException e) {
            throw new FuroCashException("Database error", e);
        }
    }

    private void discard(String mobile) {
        try (Connection conn = DBConnection.getConnection()) {
            otpDao.delete(conn, mobile);
        } catch (SQLException ignored) {
        }
    }
}