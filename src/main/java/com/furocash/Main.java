package com.furocash;

import com.furocash.util.DBConnection;

public class Main {
    public static void main(String[] args){
        DBConnection.migrate();
        System.out.println("Database ready...");
    }
}
