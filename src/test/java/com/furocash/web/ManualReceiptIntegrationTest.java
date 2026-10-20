package com.furocash.web;

import static org.junit.jupiter.api.Assertions.*;

import com.furocash.util.DBConnection;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "integration", matches = "true")
class ManualReceiptIntegrationTest extends DemoIntegrationTestSupport {

  @Test
  void manualMethodsPreserveNotesAndOwnerReceiptsWithDatabaseTimestamp()
    throws Exception {
    var owner = register(mobile(), "Manual Owner");
    var other = register(mobile(), "Other Owner");
    String token = token(owner);
    var options = call("GET", "/api/deposits/options", null, token);
    assertEquals(200, options.status());
    assertEquals(50000, options.body().path("manualMaxAmount").asDouble());
    for (String method : List.of(
      "CASH_DEMO",
      "BANK_TRANSFER_DEMO",
      "EWALLET_DEMO",
      "OTHER_DEMO"
    )) {
      var prepared = call(
        "POST",
        "/api/deposits/demo",
        Map.of("amount", "12.34", "method", method, "note", "Fixture note"),
        token,
        key()
      );
      assertEquals(201, prepared.status(), prepared.body().toString());
      String ref = prepared.body().path("reference").asText(),
        operation = key();
      assertEquals(
        404,
        call(
          "GET",
          "/api/transactions/" + ref + "/receipt",
          null,
          token
        ).status()
      );
      var done = call(
        "POST",
        "/api/deposits/demo/" + ref + "/complete",
        Map.of(),
        token,
        operation
      );
      assertEquals(200, done.status());
      assertEquals(
        Map.of(
          "CASH_DEMO",
          "Cash Deposit (Demo)",
          "BANK_TRANSFER_DEMO",
          "Bank Transfer (Demo)",
          "EWALLET_DEMO",
          "E-Wallet (Demo)",
          "OTHER_DEMO",
          "Other (Demo)"
        ).get(method),
        done.body().path("method").asText()
      );
      assertEquals("Fixture note", done.body().path("note").asText());
      assertEquals(0, done.body().path("fee").asDouble());
      assertEquals(12.34, done.body().path("total").asDouble());
      assertEquals(
        done.body(),
        call("GET", "/api/transactions/" + ref + "/receipt", null, token).body()
      );
      assertEquals(
        404,
        call(
          "GET",
          "/api/transactions/" + ref + "/receipt",
          null,
          token(other)
        ).status()
      );
      assertEquals(
        "COMPLETED",
        call("GET", "/api/operations/" + operation, null, token)
          .body()
          .path("state")
          .asText()
      );
      assertEquals(
        "NOT_FOUND",
        call("GET", "/api/operations/" + operation, null, token(other))
          .body()
          .path("state")
          .asText()
      );
      try (
        var c = DBConnection.getConnection();
        var ps = c.prepareStatement(
          "SELECT created_at FROM transactions WHERE reference_no=?"
        )
      ) {
        ps.setString(1, ref);
        try (var rs = ps.executeQuery()) {
          assertTrue(rs.next());
          assertEquals(
            rs.getTimestamp(1).toLocalDateTime().toString(),
            done.body().path("createdAt").asText()
          );
        }
      }
    }
    assertTrue(
      call("GET", "/api/transactions", null, token)
        .body()
        .get(0)
        .path("receiptAvailable")
        .asBoolean()
    );
  }

  @Test
  void manualValidationAndDisabledFundingDoNotCredit() throws Exception {
    var user = register(mobile(), "Validation");
    String token = token(user);
    for (var input : List.of(
      Map.of("amount", "50000.01", "method", "CASH_DEMO"),
      Map.of("amount", "1.00", "method", "OTHER_DEMO", "note", "x".repeat(151)),
      Map.of("amount", "0", "method", "BANK_TRANSFER_DEMO")
    ))
      assertEquals(
        400,
        call("POST", "/api/deposits/demo", input, token, key()).status()
      );
    System.setProperty("DEMO_CASH_IN", "false");
    try {
      assertFalse(
        call("GET", "/api/deposits/options", null, token)
          .body()
          .path("enabled")
          .asBoolean()
      );
      assertEquals(
        403,
        call(
          "POST",
          "/api/deposits/demo",
          Map.of("amount", "1", "method", "CASH_DEMO"),
          token,
          key()
        ).status()
      );
    } finally {
      System.setProperty("DEMO_CASH_IN", "true");
    }
    assertEquals(0, balance(token));
    assertEquals(
      401,
      call("GET", "/api/operations/abcdefgh", null, null).status()
    );
    assertEquals(
      401,
      call("GET", "/api/transactions/unknown/receipt", null, null).status()
    );
  }

  @Test
  void reviewedTransfersRejectChangesAndPersistReceiverSnapshot()
    throws Exception {
    var sender = register(mobile(), "Review Sender");
    var receiver = register(mobile(), "Review Receiver");
    String token = token(sender);
    fund(token, "100");
    var input = new HashMap<String, Object>();
    input.put("receiverAccount", account(receiver));
    input.put("amount", "25.00");
    input.put("note", "Lunch");
    var review = call("POST", "/api/transfers/review", input, token);
    assertEquals(200, review.status(), review.body().toString());
    assertEquals(
      "Review Receiver",
      review.body().path("recipient").path("fullName").asText()
    );
    assertEquals(25, review.body().path("total").asDouble());
    assertEquals(100, balance(token));
    input.put("reviewReference", review.body().path("reference").asText());
    input.put("amount", "26.00");
    assertEquals(
      409,
      call("POST", "/api/transfers", input, token, key()).status()
    );
    input.put("amount", "25.00");
    String operation = key();
    var done = call("POST", "/api/transfers", input, token, operation);
    assertEquals(200, done.status(), done.body().toString());
    assertEquals(
      "FuroCash Wallet Transfer (Demo)",
      done.body().path("method").asText()
    );
    assertEquals(
      done.body(),
      call("POST", "/api/transfers", input, token, operation).body()
    );
    assertEquals(
      409,
      call("POST", "/api/transfers", input, token, key()).status()
    );
    String ref = done.body().path("reference").asText();
    var received = call(
      "GET",
      "/api/transactions/" + ref + "/receipt",
      null,
      token(receiver)
    );
    assertEquals(200, received.status());
    assertEquals("TRANSFER_IN", received.body().path("kind").asText());
    assertEquals(25, received.body().path("balance").asDouble());
    assertEquals(
      account(sender),
      received.body().path("senderAccount").asText()
    );
    assertEquals(
      account(receiver),
      received.body().path("recipientAccount").asText()
    );
    assertEquals(75, balance(token));
    assertEquals(25, balance(token(receiver)));
  }

  @Test
  void expiredOrRenamedReviewsCannotMoveMoney() throws Exception {
    var sender = register(mobile(), "Expiry Sender");
    var receiver = register(mobile(), "Expiry Receiver");
    String token = token(sender);
    fund(token, "30");
    var payload = new HashMap<String, Object>();
    payload.put("receiverAccount", account(receiver));
    payload.put("amount", "10");
    var review = call("POST", "/api/transfers/review", payload, token);
    assertEquals(200, review.status());
    payload.put("reviewReference", review.body().path("reference").asText());
    try (
      var c = DBConnection.getConnection();
      var ps = c.prepareStatement(
        "UPDATE transfer_reviews SET expires_at=NOW()-INTERVAL '1 minute' WHERE reference=?"
      )
    ) {
      ps.setString(1, review.body().path("reference").asText());
      ps.executeUpdate();
    }
    assertEquals(
      409,
      call("POST", "/api/transfers", payload, token, key()).status()
    );
    assertEquals(30, balance(token));
  }

  @Test
  void operationStatusDistinguishesOwnerInFlightAndFailedOperationsRollback()
    throws Exception {
    var owner = register(mobile(), "Lock Owner");
    var other = register(mobile(), "Lock Other");
    String token = token(owner),
      operation = key();
    long ownerId = owner.body().path("user").path("id").asLong();
    try (
      var c = DBConnection.getConnection();
      var lock = c.prepareStatement(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))"
      )
    ) {
      c.setAutoCommit(false);
      lock.setString(1, "furocash:idem:" + ownerId + ":" + operation);
      lock.execute();
      assertEquals(
        "PROCESSING",
        call("GET", "/api/operations/" + operation, null, token)
          .body()
          .path("state")
          .asText()
      );
      assertEquals(
        "NOT_FOUND",
        call("GET", "/api/operations/" + operation, null, token(other))
          .body()
          .path("state")
          .asText()
      );
      c.rollback();
    }
    assertEquals(
      "NOT_FOUND",
      call("GET", "/api/operations/" + operation, null, token)
        .body()
        .path("state")
        .asText()
    );
    var failed = call(
      "POST",
      "/api/transfers",
      Map.of("receiverAccount", account(other), "amount", "20.00"),
      token,
      operation
    );
    assertEquals(422, failed.status());
    assertEquals(0, balance(token));
    assertEquals(0, balance(token(other)));
    assertEquals(
      "NOT_FOUND",
      call("GET", "/api/operations/" + operation, null, token)
        .body()
        .path("state")
        .asText()
    );
    assertEquals(
      0,
      call("GET", "/api/transactions", null, token).body().size()
    );
  }

  @Test
  void reviewedRecipientNameChangeRequiresAnotherReview() throws Exception {
    var sender = register(mobile(), "Name Sender");
    var receiver = register(mobile(), "Before Name");
    String token = token(sender);
    fund(token, "30");
    var payload = new HashMap<String, Object>();
    payload.put("receiverAccount", account(receiver));
    payload.put("amount", "10");
    var review = call("POST", "/api/transfers/review", payload, token);
    assertEquals(200, review.status());
    payload.put("reviewReference", review.body().path("reference").asText());
    try (
      var c = DBConnection.getConnection();
      var ps = c.prepareStatement(
        "UPDATE users SET full_name='After Name' WHERE id=?"
      )
    ) {
      ps.setLong(1, receiver.body().path("user").path("id").asLong());
      ps.executeUpdate();
    }
    assertEquals(
      409,
      call("POST", "/api/transfers", payload, token, key()).status()
    );
    assertEquals(30, balance(token));
    assertEquals(0, balance(token(receiver)));
  }

  @Test
  void concurrentReviewedConfirmationsWithDifferentKeysPayOnceAndReceiptsAreImmutable()
    throws Exception {
    var sender = register(mobile(), "Concurrent Review Sender");
    var receiver = register(mobile(), "Concurrent Review Receiver");
    String token = token(sender);
    fund(token, "100");
    var payload = new HashMap<String, Object>();
    payload.put("receiverAccount", account(receiver));
    payload.put("amount", "20");
    var review = call("POST", "/api/transfers/review", payload, token);
    assertEquals(200, review.status());
    payload.put("reviewReference", review.body().path("reference").asText());
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() ->
        call("POST", "/api/transfers", payload, token, key())
      );
      var b = pool.submit(() ->
        call("POST", "/api/transfers", payload, token, key())
      );
      var statuses = new ArrayList<>(
        List.of(
          a.get(30, java.util.concurrent.TimeUnit.SECONDS).status(),
          b.get(30, java.util.concurrent.TimeUnit.SECONDS).status()
        )
      );
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
    }
    assertEquals(80, balance(token));
    assertEquals(20, balance(token(receiver)));
    try (
      var c = DBConnection.getConnection();
      var ps = c.prepareStatement(
        "UPDATE transaction_receipts SET receipt='{}'::jsonb WHERE user_id=?"
      )
    ) {
      ps.setLong(1, sender.body().path("user").path("id").asLong());
      assertThrows(java.sql.SQLException.class, ps::executeUpdate);
    }
  }

  @Test
  void manualConfiguredMaximumAcceptsBoundaryAndRejectsExcess()
    throws Exception {
    var owner = register(mobile(), "Configured Max");
    String token = token(owner);
    System.setProperty("DEMO_MANUAL_DEPOSIT_MAX", "25.50");
    try {
      assertEquals(
        25.50,
        call("GET", "/api/deposits/options", null, token)
          .body()
          .path("manualMaxAmount")
          .asDouble()
      );
      assertEquals(
        201,
        call(
          "POST",
          "/api/deposits/demo",
          Map.of(
            "amount",
            "25.50",
            "method",
            "CASH_DEMO",
            "note",
            "x".repeat(150)
          ),
          token,
          key()
        ).status()
      );
      assertEquals(
        400,
        call(
          "POST",
          "/api/deposits/demo",
          Map.of("amount", "25.51", "method", "CASH_DEMO"),
          token,
          key()
        ).status()
      );
      assertEquals(0, balance(token));
    } finally {
      System.clearProperty("DEMO_MANUAL_DEPOSIT_MAX");
    }
  }

  @Test
  void receiptPersistenceFailureRollsBackBalanceHistoryAndIdempotency()
    throws Exception {
    var owner = register(mobile(), "Atomic Receipt");
    String token = token(owner),
      operation = key();
    var prepared = call(
      "POST",
      "/api/deposits/demo",
      Map.of("amount", "10.00", "method", "CASH_DEMO"),
      token,
      key()
    );
    assertEquals(201, prepared.status());
    String reference = prepared.body().path("reference").asText();
    try (var c = DBConnection.getConnection(); var q = c.createStatement()) {
      q.execute(
        "CREATE FUNCTION reject_test_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'isolated simulated receipt persistence failure'; END; $$"
      );
      q.execute(
        "CREATE TRIGGER test_receipt_failure BEFORE INSERT ON transaction_receipts FOR EACH ROW EXECUTE FUNCTION reject_test_receipt()"
      );
      try {
        assertEquals(
          500,
          call(
            "POST",
            "/api/deposits/demo/" + reference + "/complete",
            Map.of(),
            token,
            operation
          ).status()
        );
        assertEquals(0, balance(token));
        assertEquals(
          0,
          call("GET", "/api/transactions", null, token).body().size()
        );
        assertEquals(
          "NOT_FOUND",
          call("GET", "/api/operations/" + operation, null, token)
            .body()
            .path("state")
            .asText()
        );
      } finally {
        q.execute("DROP TRIGGER test_receipt_failure ON transaction_receipts");
        q.execute("DROP FUNCTION reject_test_receipt()");
      }
    }
    assertEquals(
      200,
      call(
        "POST",
        "/api/deposits/demo/" + reference + "/complete",
        Map.of(),
        token,
        operation
      ).status()
    );
    assertEquals(10, balance(token));
  }

  @Test
  void unsupportedLegacyHistoryNeverFabricatesReceipts() throws Exception {
    var owner = register(mobile(), "Legacy Receipt");
    String token = token(owner);
    try (
      var c = DBConnection.getConnection();
      var ps = c.prepareStatement(
        "INSERT INTO transactions(user_id,type,amount,details,reference_no,balance_after) VALUES (?,'CASH_IN',5,'Historical receipt unavailable','UNSUPPORTED-LEGACY',5)"
      )
    ) {
      ps.setLong(1, owner.body().path("user").path("id").asLong());
      ps.executeUpdate();
    }
    assertFalse(
      call("GET", "/api/transactions", null, token)
        .body()
        .get(0)
        .path("receiptAvailable")
        .asBoolean()
    );
    assertEquals(
      404,
      call(
        "GET",
        "/api/transactions/UNSUPPORTED-LEGACY/receipt",
        null,
        token
      ).status()
    );
  }

  @Test
  void controlCharactersInNotesReturnValidationErrorsBeforeDatabaseAccess()
    throws Exception {
    var sender = register(mobile(), "Control Sender");
    var receiver = register(mobile(), "Control Receiver");
    String token = token(sender);
    fund(token, "20");
    for (String note : List.of(
      "\u0000",
      "before\u0000after",
      "before\nafter",
      "before\tafter",
      "before\u007fafter"
    )) {
      assertEquals(
        400,
        call(
          "POST",
          "/api/deposits/demo",
          Map.of("amount", "1", "method", "CASH_DEMO", "note", note),
          token,
          key()
        ).status()
      );
      var payload = Map.of(
        "receiverAccount",
        account(receiver),
        "amount",
        "1",
        "note",
        note
      );
      assertEquals(
        400,
        call("POST", "/api/transfers/review", payload, token).status()
      );
      assertEquals(
        400,
        call("POST", "/api/transfers", payload, token, key()).status()
      );
    }
    assertEquals(20, balance(token));
    assertEquals(0, balance(token(receiver)));
  }

  @Test
  void recipientRenameWhileConfirmationWaitsForAccountLockInvalidatesReview()
    throws Exception {
    var sender = register(mobile(), "Waiting Sender");
    var receiver = register(mobile(), "Waiting Receiver");
    String token = token(sender);
    fund(token, "30");
    var payload = new HashMap<String, Object>();
    payload.put("receiverAccount", account(receiver));
    payload.put("amount", "10");
    var review = call("POST", "/api/transfers/review", payload, token);
    assertEquals(200, review.status());
    payload.put("reviewReference", review.body().path("reference").asText());
    long receiverId = receiver.body().path("user").path("id").asLong();
    try (
      var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
      var blocker = DBConnection.getConnection()
    ) {
      blocker.setAutoCommit(false);
      int blockerPid;
      try (
        var q = blocker.createStatement();
        var rs = q.executeQuery("SELECT pg_backend_pid()")
      ) {
        rs.next();
        blockerPid = rs.getInt(1);
      }
      try (
        var lock = blocker.prepareStatement(
          "SELECT id FROM users WHERE id=? FOR UPDATE"
        )
      ) {
        lock.setLong(1, receiverId);
        try (var rs = lock.executeQuery()) {
          assertTrue(rs.next());
        }
      }
      var confirmation = pool.submit(() ->
        call("POST", "/api/transfers", payload, token, key())
      );
      boolean waiting = false;
      long deadline =
        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
      try (
        var observer = DBConnection.getConnection();
        var query = observer.prepareStatement(
          "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))"
        )
      ) {
        query.setInt(1, blockerPid);
        while (System.nanoTime() < deadline) {
          try (var rs = query.executeQuery()) {
            rs.next();
            waiting = rs.getBoolean(1);
          }
          if (waiting) break;
          Thread.sleep(25);
        }
      }
      try {
        assertTrue(
          waiting,
          "Confirmation must wait for the receiver account lock before the rename"
        );
        try (
          var rename = blocker.prepareStatement(
            "UPDATE users SET full_name='Changed While Waiting' WHERE id=?"
          )
        ) {
          rename.setLong(1, receiverId);
          rename.executeUpdate();
        }
        blocker.commit();
        assertEquals(
          409,
          confirmation.get(30, java.util.concurrent.TimeUnit.SECONDS).status()
        );
      } finally {
        blocker.rollback();
      }
    }
    assertEquals(30, balance(token));
    assertEquals(0, balance(token(receiver)));
  }
}
