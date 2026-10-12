package com.furocash;

import com.furocash.model.Transaction;
import com.furocash.model.User;
import com.furocash.service.AccountService;
import com.furocash.service.AuthService;
import com.furocash.util.DBConnection;


public class Main {
    public static void main(String[] args) {
        DBConnection.migrate();
        System.out.println("Database ready...");

        AuthService auth = new AuthService();
        AccountService account = new AccountService();

        for (String mobile : new String[]{"09171234567", "09181234567"}) {
            User u = auth.login(mobile, "1234");
            System.out.println("History for " + u.getFullName() + ":");
            for (Transaction t : account.getHistory(u.getId())) {
                System.out.println("  " + t.getCreatedAt() + " " + t.getType() + " " + t.getAmount()
                        + " -> balance " + t.getBalanceAfter() + " (" + t.getDetails() + ")");
            }
        }

    }
}