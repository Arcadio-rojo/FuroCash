package com.furocash;

import com.furocash.dao.UserDao;
import com.furocash.model.User;
import com.furocash.util.PinHasher;
import com.furocash.util.DBConnection;
import com.furocash.dao.TransactionDao;
import com.furocash.model.Transaction;
import com.furocash.model.TransactionType;
import java.util.List;
import com.furocash.service.AccountService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

import com.furocash.model.User;
import com.furocash.service.AuthService;
import com.furocash.exception.FuroCashException;
import com.furocash.util.DBConnection;

public class Main {
    public static void main(String[] args) {
        DBConnection.migrate();
        System.out.println("Database ready...");

        AuthService auth = new AuthService();
        AccountService account = new AccountService();

        User u = auth.login("09171234567", "1234");
        System.out.println("Balance: " + account.getBalance(u.getId()));
        System.out.println("After cash-in: " + account.cashIn(u.getId(), new BigDecimal("250.50")));

        for (String bad : new String[]{"-5", "0", "10.999"}) {
            try {
                account.cashIn(u.getId(), new BigDecimal(bad));
            } catch (FuroCashException e) {
                System.out.println("Rejected " + bad + ": " + e.getMessage());
            }
        }
        System.out.println("Balance: " + account.getBalance(u.getId()));
        }

    }


