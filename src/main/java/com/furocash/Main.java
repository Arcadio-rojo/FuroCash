package com.furocash;

import com.furocash.dao.UserDao;
import com.furocash.model.User;
import com.furocash.util.PinHasher;
import com.furocash.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args){
        DBConnection.migrate();
        System.out.println("Database ready...");

        try (Connection conn = DBConnection.getConnection()) {
            UserDao dao = new UserDao();

            User u = new User();
            u.setMobileNumber("09171234567");
            u.setPinHash(PinHasher.hash("1234"));
            u.setFullName("Test User");
            u.setBalance(BigDecimal.ZERO);

            long id = dao.insert(conn, u);
            System.out.println("Inserted id: " + id);

            dao.updateBalance(conn, id, new BigDecimal("500.00"));

            User found = dao.findByMobile(conn, "09171234567").orElseThrow();
            System.out.println(found.getFullName() + " has " + found.getBalance());
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR IN MAIN TEST:");
            e.printStackTrace();
        }
    }
}
