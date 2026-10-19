package com.furocash.service;

import com.furocash.dao.TransactionDao;
import com.furocash.dao.UserDao;
import com.furocash.exception.FuroCashException;
import com.furocash.exception.InvalidAmountException;
import com.furocash.exception.UserNotFoundException;
import com.furocash.model.Transaction;
import com.furocash.model.TransactionType;
import com.furocash.model.User;
import com.furocash.util.DBConnection;
import com.furocash.util.InputValidator;
import com.furocash.util.ReferenceNumber;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AccountService {

    private final UserDao userDao = new UserDao();
    private final TransactionDao transactionDao = new TransactionDao();

    // E: show the stored balance
    public BigDecimal getBalance(long userId) {
        try (Connection conn = DBConnection.getConnection()) {
            User user = userDao.findById(conn, userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
            return user.getBalance();
        } catch (SQLException e) {
            throw new FuroCashException("Database error", e);
        }
    }

    // F: add money and log it, all or nothing
    public BigDecimal cashIn(long userId, BigDecimal amount) {
        if (!InputValidator.isValidAmount(amount)) {
            throw new InvalidAmountException("Amount must be greater than zero, with at most 2 decimal places");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                User user = userDao.findByIdForUpdate(conn, userId)
                        .orElseThrow(() -> new UserNotFoundException("User not found"));

                BigDecimal newBalance = user.getBalance().add(amount);
                userDao.updateBalance(conn, userId, newBalance);

                Transaction tx = new Transaction();
                tx.setUserId(userId);
                tx.setType(TransactionType.CASH_IN);
                tx.setAmount(amount);
                tx.setDetails("Cash in");
                tx.setReferenceNo(ReferenceNumber.generate());
                tx.setBalanceAfter(newBalance);
                transactionDao.insert(conn, tx);

                conn.commit();
                return newBalance;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new FuroCashException("Database error", e);
        }
    }

    public List<Transaction> getHistory(long userId) {
        try (Connection conn = DBConnection.getConnection()) {
            userDao.findById(conn, userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
            return transactionDao.findByUserId(conn, userId);
        } catch (SQLException e) {
            throw new FuroCashException("Database error", e);
        }
    }

    public List<Transaction> getHistory(long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page, 0) * safeSize;
        try (Connection conn = DBConnection.getConnection()) {
            userDao.findById(conn, userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
            return transactionDao.findByUserId(conn, userId, safeSize, offset);
        } catch (SQLException e) {
            throw new FuroCashException("Database error", e);
        }
    }


}