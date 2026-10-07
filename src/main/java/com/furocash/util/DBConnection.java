package com.furocash.util;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.SQLException;

public final class DBConnection {
    private DBConnection() {
    }


    private static final HikariDataSource DATA_SOURCE = createPool();


    private static HikariDataSource createPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5433/" + System.getenv("DB_NAME"));        config.setUsername(System.getenv("DB_USER"));
        config.setPassword(System.getenv("DB_PASSWORD"));
        config.setMaximumPoolSize(10);
        config.setPoolName("furocash-pool");

        return new HikariDataSource(config);


    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public static void migrate() {
        Flyway.configure().dataSource(DATA_SOURCE).load().migrate();
    }

}