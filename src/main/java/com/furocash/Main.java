package com.furocash;

import com.furocash.dao.UserDao;
import com.furocash.model.User;
import com.furocash.util.PinHasher;
import com.furocash.util.DBConnection;
import com.furocash.dao.TransactionDao;
import com.furocash.model.Transaction;
import com.furocash.model.TransactionType;
import java.util.List;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        DBConnection.migrate();
        System.out.println("Database ready...");

        try (Connection conn = DBConnection.getConnection()) {
            UserDao userDao = new UserDao();
            TransactionDao transactionDao = new TransactionDao();

            // 1. Insert a test user
            com.furocash.model.User u = new com.furocash.model.User();
            u.setMobileNumber("09179998888");
            u.setPinHash(com.furocash.util.PinHasher.hash("1234"));
            u.setFullName("Tx Test User");
            u.setBalance(java.math.BigDecimal.ZERO);
            long userId = userDao.insert(conn, u);

            // 2. Build and insert a transaction
            Transaction tx = new Transaction();
            tx.setUserId(userId);
            tx.setType(TransactionType.CASH_IN);
            tx.setAmount(new java.math.BigDecimal("100.00"));
            tx.setDetails("Test cash-in");
            tx.setReferenceNo("FC-TEST-0001");
            tx.setBalanceAfter(new java.math.BigDecimal("100.00"));

            long txId = transactionDao.insert(conn, tx);
            System.out.println("Inserted transaction id: " + txId);

            // 3. Find transactions by user ID
            List<Transaction> history = transactionDao.findByUserId(conn, userId);
            System.out.println("Total transactions found: " + history.size());
            if (!history.isEmpty()) {
                System.out.println("First record type: " + history.get(0).getType() + ", Amount: " + history.get(0).getAmount());
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }
}
