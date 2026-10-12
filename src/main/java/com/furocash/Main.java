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

import com.furocash.service.TransferService;

public class Main {
    public static void main(String[] args) {
        DBConnection.migrate();
        System.out.println("Database ready...");

        AuthService auth = new AuthService();
        TransferService transfers = new TransferService();
        AccountService account = new AccountService();

        try {
            auth.register("09181234567", "1234", "Receiver User");
        } catch (FuroCashException e) {
            System.out.println("Register: " + e.getMessage());
        }

        User sender = auth.login("09171234567", "1234");
        User receiver = auth.login("09181234567", "1234");

        System.out.println("Sent. Sender balance: " + transfers.transfer(sender.getId(), "09181234567", new BigDecimal("100.00")));

        String[][] bad = {
                {"09171234567", "10.00"},    // to yourself
                {"09999999999", "10.00"},    // receiver not registered
                {"09181234567", "9999.00"},  // more than the balance
                {"09181234567", "0"}         // invalid amount
        };
        for (String[] b : bad) {
            try {
                transfers.transfer(sender.getId(), b[0], new BigDecimal(b[1]));
            } catch (FuroCashException e) {
                System.out.println("Rejected " + b[0] + " / " + b[1] + ": " + e.getMessage());
            }
        }

        System.out.println("Sender: " + account.getBalance(sender.getId()));
        System.out.println("Receiver: " + account.getBalance(receiver.getId()));
        }

    }


