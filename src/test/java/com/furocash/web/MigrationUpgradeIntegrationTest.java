package com.furocash.web;

import static org.junit.jupiter.api.Assertions.*;

import com.furocash.util.AppConfig;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "integration", matches = "true")
class MigrationUpgradeIntegrationTest {

  @Test
  void bankingMigrationPreservesExistingUsersBalancesAndHistory()
    throws Exception {
    String url = AppConfig.get(
      "TEST_DB_URL",
      AppConfig.get(
        "DB_URL",
        "jdbc:postgresql://localhost:5433/" +
          AppConfig.get("DB_NAME", "furocash")
      )
    );
    String user = AppConfig.get("DB_USER", "furocash");
    String password = AppConfig.get("DB_PASSWORD", "");
    String schema =
      "furocash_upgrade_test_" + UUID.randomUUID().toString().replace("-", "");
    try (
      var connection = DriverManager.getConnection(url, user, password);
      var statement = connection.createStatement()
    ) {
      statement.execute("CREATE SCHEMA " + schema);
      try {
        String isolatedUrl =
          url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        Flyway.configure()
          .dataSource(isolatedUrl, user, password)
          .target("3")
          .load()
          .migrate();
        try (
          var oldConnection = DriverManager.getConnection(
            isolatedUrl,
            user,
            password
          );
          var oldStatement = oldConnection.createStatement()
        ) {
          oldStatement.executeUpdate(
            "INSERT INTO users(mobile_number,pin_hash,full_name,balance,session_version) VALUES ('09900000001','existing-test-hash','Existing Customer',123.45,3)"
          );
          oldStatement.executeUpdate(
            "INSERT INTO transactions(user_id,type,amount,details,reference_no,balance_after) SELECT id,'CASH_IN',123.45,'Existing history','EXISTING-REFERENCE',123.45 FROM users"
          );
        }
        assertEquals(
          1,
          Flyway.configure()
            .dataSource(isolatedUrl, user, password)
            .load()
            .migrate()
            .migrationsExecuted
        );
        try (
          var upgraded = DriverManager.getConnection(
            isolatedUrl,
            user,
            password
          );
          var query = upgraded.createStatement()
        ) {
          try (
            var result = query.executeQuery(
              "SELECT mobile_number,pin_hash,full_name,balance,session_version,email,created_at FROM users"
            )
          ) {
            assertTrue(result.next());
            assertEquals("09900000001", result.getString("mobile_number"));
            assertEquals("existing-test-hash", result.getString("pin_hash"));
            assertEquals("Existing Customer", result.getString("full_name"));
            assertEquals(
              new BigDecimal("123.45"),
              result.getBigDecimal("balance")
            );
            assertEquals(3, result.getInt("session_version"));
            assertNull(result.getString("email"));
            assertNotNull(result.getTimestamp("created_at"));
            assertFalse(result.next());
          }
          try (
            var result = query.executeQuery(
              "SELECT reference_no,details,amount,balance_after,status,card_id FROM transactions"
            )
          ) {
            assertTrue(result.next());
            assertEquals(
              "EXISTING-REFERENCE",
              result.getString("reference_no")
            );
            assertEquals("Existing history", result.getString("details"));
            assertEquals(
              new BigDecimal("123.45"),
              result.getBigDecimal("amount")
            );
            assertEquals(
              new BigDecimal("123.45"),
              result.getBigDecimal("balance_after")
            );
            assertEquals("COMPLETED", result.getString("status"));
            assertNull(result.getObject("card_id"));
            assertFalse(result.next());
          }
          assertThrows(SQLException.class, () ->
            query.executeUpdate("DELETE FROM transactions")
          );
        }
      } finally {
        statement.execute("DROP SCHEMA " + schema + " CASCADE");
      }
    }
  }
}
