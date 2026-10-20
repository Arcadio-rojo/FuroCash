package com.furocash.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;

public final class DBConnection {

  private DBConnection() {}

  private static class Pool {

    private static final HikariDataSource INSTANCE = createPool();
  }

  private static HikariDataSource createPool() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(
      AppConfig.get(
        "DB_URL",
        "jdbc:postgresql://localhost:5433/" +
          AppConfig.get("DB_NAME", "furocash")
      )
    );
    config.setUsername(AppConfig.get("DB_USER", "furocash"));
    config.setPassword(AppConfig.get("DB_PASSWORD", ""));
    config.setMaximumPoolSize(10);
    config.setConnectionTimeout(10000);
    config.setPoolName("furocash-pool");
    return new HikariDataSource(config);
  }

  public static Connection getConnection() throws SQLException {
    return Pool.INSTANCE.getConnection();
  }

  public static void migrate() {
    Flyway.configure().dataSource(Pool.INSTANCE).load().migrate();
  }
}
