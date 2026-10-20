package com.furocash.web;

import static org.junit.jupiter.api.Assertions.*;

import com.furocash.util.AppConfig;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "integration", matches = "true")
class ManualMigrationIntegrationTest {

  @Test
  void populatedV4UpgradePreservesDepositsOperationsCardsAndLegacyHistory()
    throws Exception {
    String url = AppConfig.get(
      "TEST_DB_URL",
      AppConfig.get(
        "DB_URL",
        "jdbc:postgresql://localhost:5433/" +
          AppConfig.get("DB_NAME", "furocash")
      )
    );
    String username = AppConfig.get("DB_USER", "furocash"),
      password = AppConfig.get("DB_PASSWORD", "");
    String schema =
      "manual_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    try (
      var c = DriverManager.getConnection(url, username, password);
      var s = c.createStatement()
    ) {
      s.execute("CREATE SCHEMA " + schema);
      try {
        String isolated =
          url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        Flyway.configure()
          .dataSource(isolated, username, password)
          .target("4")
          .load()
          .migrate();
        try (
          var old = DriverManager.getConnection(isolated, username, password);
          var q = old.createStatement()
        ) {
          q.execute(
            "INSERT INTO users(mobile_number,pin_hash,full_name,balance) VALUES ('09912345678','unchanged-hash','Preserved',123.45)"
          );
          q.execute(
            "INSERT INTO transactions(user_id,type,amount,details,reference_no,balance_after) SELECT id,'CASH_IN',123.45,'Legacy history','LEGACY-V4',123.45 FROM users"
          );
          q.execute(
            "INSERT INTO demo_cards(id,user_id,identifier,expires_on) SELECT '11111111-1111-1111-1111-111111111111',id,'Preserved card',CURRENT_DATE+365 FROM users"
          );
          q.execute(
            "INSERT INTO demo_deposits(reference,user_id,method,amount) SELECT 'PENDING-V4',id,'GCASH_DEMO',5 FROM users"
          );
          q.execute(
            "INSERT INTO idempotency_operations(user_id,idempotency_key,operation,request_hash,response) SELECT id,'legacy-key','DEPOSIT_PREPARE','legacy-hash','{\"status\":\"PENDING\"}'::jsonb FROM users"
          );
        }
        assertEquals(
          1,
          Flyway.configure()
            .dataSource(isolated, username, password)
            .load()
            .migrate()
            .migrationsExecuted
        );
        try (
          var upgraded = DriverManager.getConnection(
            isolated,
            username,
            password
          );
          var q = upgraded.createStatement()
        ) {
          try (var r = q.executeQuery("SELECT balance,pin_hash FROM users")) {
            assertTrue(r.next());
            assertEquals("123.45", r.getBigDecimal(1).toPlainString());
            assertEquals("unchanged-hash", r.getString(2));
          }
          try (
            var r = q.executeQuery(
              "SELECT details,reference_no FROM transactions"
            )
          ) {
            assertTrue(r.next());
            assertEquals("Legacy history", r.getString(1));
            assertEquals("LEGACY-V4", r.getString(2));
          }
          for (String table : java.util.List.of(
            "demo_cards",
            "demo_deposits",
            "idempotency_operations"
          ))
            try (var r = q.executeQuery("SELECT count(*) FROM " + table)) {
              r.next();
              assertEquals(1, r.getInt(1));
            }
          try (
            var r = q.executeQuery("SELECT count(*) FROM transaction_receipts")
          ) {
            r.next();
            assertEquals(0, r.getInt(1));
          }
          assertThrows(SQLException.class, () ->
            q.executeUpdate("UPDATE transactions SET details='tampered'")
          );
        }
      } finally {
        s.execute("DROP SCHEMA " + schema + " CASCADE");
      }
    }
  }
}
