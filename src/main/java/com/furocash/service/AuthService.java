package com.furocash.service;

import com.furocash.dao.UserDao;
import com.furocash.exception.FuroCashException;
import com.furocash.exception.InvalidInputException;
import com.furocash.exception.UserAlreadyExistsException;
import com.furocash.model.User;
import com.furocash.util.DBConnection;
import com.furocash.util.InputValidator;
import com.furocash.util.PinHasher;

import com.furocash.exception.AccountLockedException;
import com.furocash.exception.InvalidCredentialsException;
import java.time.Duration;
import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

public class AuthService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int LOCK_MINUTES = 5;

    private final UserDao userDao = new UserDao();

    public long register(String mobile, String pin, String fullName) {
        if (!InputValidator.isValidMobile(mobile)) {
            throw new InvalidInputException("Mobile number must be 11 digits starting with 09");
        }
        if (!InputValidator.isValidPin(pin)) {
            throw new InvalidInputException("PIN must be exactly 4 digits");
        }
        if (!InputValidator.isValidFullName(fullName)) {
            throw new InvalidInputException("Full name is required (max 100 characters)");
        }

        try (Connection conn = DBConnection.getConnection()) {
            if (userDao.findByMobile(conn, mobile).isPresent()) {
                throw new UserAlreadyExistsException("Mobile number is already registered");
            }

            User user = new User();
            user.setMobileNumber(mobile);
            user.setPinHash(PinHasher.hash(pin));
            user.setFullName(fullName.trim());
            user.setBalance(BigDecimal.ZERO);

            return userDao.insert(conn, user);
        } catch (SQLException e) {
            throw new FuroCashException("Database error", e);
        }
    }
    public User login(String mobile, String pin) {
        if (!InputValidator.isValidMobile(mobile)) {
            throw new InvalidInputException("Mobile number must be 11 digits starting with 09");
        }
        if (!InputValidator.isValidPin(pin)) {
            throw new InvalidInputException("PIN must be exactly 4 digits");
        }

        try (Connection conn = DBConnection.getConnection()) {
            User user = userDao.findByMobile(conn, mobile)
                    .orElseThrow(() -> new InvalidCredentialsException("Mobile number is not registered"));

            LocalDateTime now = LocalDateTime.now();

            if (user.getLockedUntil() != null) {
                if (user.getLockedUntil().isAfter(now)) {
                    long minutes = Duration.between(now, user.getLockedUntil()).toMinutes() + 1;
                    throw new AccountLockedException("Account is locked. Try again in " + minutes + " minute(s)");
                }
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
                userDao.updateLoginAttempts(conn, user.getId(), 0, null);
            }

            if (!PinHasher.matches(pin, user.getPinHash())) {
                int attempts = user.getFailedAttempts() + 1;

                if (attempts >= MAX_ATTEMPTS) {
                    LocalDateTime lockUntil = now.plusMinutes(LOCK_MINUTES);
                    userDao.updateLoginAttempts(conn, user.getId(), attempts, lockUntil);
                    throw new AccountLockedException(
                            "Too many failed attempts. Account locked for " + LOCK_MINUTES + " minutes");
                }

                userDao.updateLoginAttempts(conn, user.getId(), attempts, null);
                int left = MAX_ATTEMPTS - attempts;
                throw new InvalidCredentialsException("Incorrect PIN. " + left + " attempt(s) left");
            }

            if (user.getFailedAttempts() > 0) {
                userDao.updateLoginAttempts(conn, user.getId(), 0, null);
                user.setFailedAttempts(0);
            }
            return user;
        } catch (SQLException e) {
            throw new FuroCashException("Database error", e);
        }
    }

}