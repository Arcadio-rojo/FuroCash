package com.furocash.service;

import com.furocash.dao.TransactionDao;
import com.furocash.dao.UserDao;
import com.furocash.exception.FuroCashException;
import com.furocash.exception.InsufficientFundsException;
import com.furocash.exception.InvalidAmountException;
import com.furocash.exception.InvalidInputException;
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

public class TransferService {

    private final UserDao userDao = new UserDao();
    private final TransactionDao transactionDao = new TransactionDao();

    public BigDecimal transfer(long senderId, String receiverMobile, BigDecimal amount) {
        if (!InputValidator.isValidAmount(amount)) {
            throw new InvalidAmountException("Amount must be greater than zero, with at most 2 decimal places");
        }
        if (!InputValidator.isValidMobile(receiverMobile)) {
            throw new InvalidInputException("Receiver mobile number must be 11 digits starting with 09");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                User receiverLookup = userDao.findByMobile(conn, receiverMobile)
                        .orElseThrow(() -> new UserNotFoundException("Receiver not found"));
                long receiverId = receiverLookup.getId();

                if (receiverId == senderId) {
                    throw new InvalidInputException("You cannot transfer money to yourself");
                }

                long firstId = Math.min(senderId, receiverId);
                long secondId = Math.max(senderId, receiverId);
                User first = userDao.findByIdForUpdate(conn, firstId)
                        .orElseThrow(() -> new UserNotFoundException("User not found"));
                User second = userDao.findByIdForUpdate(conn, secondId)
                        .orElseThrow(() -> new UserNotFoundException("User not found"));
                User sender = (firstId == senderId) ? first : second;
                User receiver = (firstId == senderId) ? second : first;

                if (sender.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientFundsException("Insufficient balance");
                }

                BigDecimal senderNewBalance = sender.getBalance().subtract(amount);
                BigDecimal receiverNewBalance = receiver.getBalance().add(amount);
                userDao.updateBalance(conn, sender.getId(), senderNewBalance);
                userDao.updateBalance(conn, receiver.getId(), receiverNewBalance);

                String ref = ReferenceNumber.generate();
                transactionDao.insert(conn, buildTransaction(sender.getId(), TransactionType.TRANSFER_OUT,
                        amount, "Sent to " + receiver.getMobileNumber(), ref, senderNewBalance));
                transactionDao.insert(conn, buildTransaction(receiver.getId(), TransactionType.TRANSFER_IN,
                        amount, "Received from " + sender.getMobileNumber(), ref, receiverNewBalance));

                conn.commit();
                return senderNewBalance;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new FuroCashException("Database error", e);
        }
    }

    private Transaction buildTransaction(long userId, TransactionType type, BigDecimal amount,
                                         String details, String ref, BigDecimal balanceAfter) {
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setDetails(details);
        tx.setReferenceNo(ref);
        tx.setBalanceAfter(balanceAfter);
        return tx;
    }
}