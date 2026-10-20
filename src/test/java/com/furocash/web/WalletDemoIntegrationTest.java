package com.furocash.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.furocash.util.DBConnection;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
  named = "integration",
  matches = "true"
)
class WalletDemoIntegrationTest extends DemoIntegrationTestSupport {

  @Test
  void depositsRequireConfirmationAndCannotBeCompletedTwice() throws Exception {
    var user = register(mobile(), "Deposit User");
    String token = token(user),
      prepareKey = key(),
      completeKey = key();
    var prepared = call(
      "POST",
      "/api/deposits/demo",
      Map.of("amount", "100.50", "method", "GCASH_DEMO"),
      token,
      prepareKey
    );
    assertEquals(201, prepared.status(), prepared.body().toString());
    assertEquals("PENDING", prepared.body().path("status").asText());
    assertEquals(0, balance(token));
    String ref = prepared.body().path("reference").asText();
    assertEquals(
      ref,
      call(
        "POST",
        "/api/deposits/demo",
        Map.of("amount", "100.50", "method", "GCASH_DEMO"),
        token,
        prepareKey
      )
        .body()
        .path("reference")
        .asText()
    );
    var completed = call(
      "POST",
      "/api/deposits/demo/" + ref + "/complete",
      Map.of(),
      token,
      completeKey
    );
    assertEquals(200, completed.status());
    assertEquals("COMPLETED", completed.body().path("status").asText());
    assertEquals(100.50, balance(token), 0.001);
    assertEquals(
      completed.body(),
      call(
        "POST",
        "/api/deposits/demo/" + ref + "/complete",
        Map.of(),
        token,
        completeKey
      ).body()
    );
    assertEquals(
      200,
      call(
        "POST",
        "/api/deposits/demo/" + ref + "/complete",
        Map.of(),
        token,
        key()
      ).status()
    );
    assertEquals(100.50, balance(token), 0.001);
    assertEquals(
      1,
      call("GET", "/api/transactions", null, token).body().size()
    );
    var other = register(mobile(), "Other User");
    assertEquals(
      404,
      call(
        "POST",
        "/api/deposits/demo/" + ref + "/complete",
        Map.of(),
        token(other),
        key()
      ).status()
    );
  }

  @Test
  void transferUsesAuthoritativeRecipientAndIdempotency() throws Exception {
    var sender = register(mobile(), "Sender");
    var receiver = register(mobile(), "Receiver");
    String token = token(sender);
    fund(token, "100.00");
    var validated = call(
      "POST",
      "/api/transfers/validate-recipient",
      Map.of("accountIdentifier", account(receiver)),
      token
    );
    assertEquals(200, validated.status());
    assertEquals("Receiver", validated.body().path("fullName").asText());
    String key = key();
    var payload = Map.of(
      "receiverAccount",
      account(receiver),
      "amount",
      "25.50",
      "note",
      "Lunch"
    );
    var transfer = call("POST", "/api/transfers", payload, token, key);
    assertEquals(200, transfer.status(), transfer.body().toString());
    assertEquals(74.50, transfer.body().path("balance").asDouble(), 0.001);
    assertEquals(
      transfer.body(),
      call("POST", "/api/transfers", payload, token, key).body()
    );
    assertEquals(25.50, balance(token(receiver)), 0.001);
    assertEquals(
      409,
      call(
        "POST",
        "/api/transfers",
        Map.of(
          "receiverAccount",
          account(receiver),
          "amount",
          "26.00",
          "note",
          "Lunch"
        ),
        token,
        key
      ).status()
    );
    assertEquals(
      422,
      call(
        "POST",
        "/api/transfers",
        Map.of("receiverAccount", account(receiver), "amount", "999.00"),
        token,
        key()
      ).status()
    );
    assertEquals(
      400,
      call(
        "POST",
        "/api/transfers",
        Map.of("receiverAccount", account(sender), "amount", "1.00"),
        token,
        key()
      ).status()
    );
    assertEquals(400, call("POST", "/api/transfers", payload, token).status());
    assertEquals(74.50, balance(token), 0.001);
  }

  @Test
  void concurrentDuplicateTransfersDebitOnce() throws Exception {
    var sender = register(mobile(), "Sender");
    var receiver = register(mobile(), "Receiver");
    String token = token(sender);
    fund(token, "100.00");
    String operationKey = key();
    var payload = Map.of(
      "receiverAccount",
      account(receiver),
      "amount",
      "25.00"
    );
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() ->
        call("POST", "/api/transfers", payload, token, operationKey)
      );
      var second = pool.submit(() ->
        call("POST", "/api/transfers", payload, token, operationKey)
      );
      var a = first.get();
      var b = second.get();
      assertEquals(200, a.status());
      assertEquals(a.body(), b.body());
    }
    assertEquals(75, balance(token), 0.001);
    assertEquals(25, balance(token(receiver)), 0.001);
  }

  @Test
  void qrMetadataCannotChangeRecipientAmountOrSchemaAndRequestsAreSingleUse()
    throws Exception {
    var sender = register(mobile(), "Sender");
    var receiver = register(mobile(), "Receiver");
    String token = token(sender);
    fund(token, "100.00");
    var generated = call(
      "POST",
      "/api/qr/requests",
      Map.of("purpose", "TRANSFER", "amount", "15.00"),
      token(receiver)
    );
    assertEquals(201, generated.status(), generated.body().toString());
    ObjectNode qr = (ObjectNode) generated.body();
    assertEquals("FUROCASH", qr.path("app").asText());
    assertEquals(1, qr.path("version").asInt());
    var resolved = call(
      "POST",
      "/api/qr/resolve",
      Map.of("payload", qr),
      token
    );
    assertEquals(200, resolved.status());
    assertEquals(
      "Receiver",
      resolved.body().path("recipient").path("fullName").asText()
    );
    var changed = qr.deepCopy();
    changed.put("amount", "1.00");
    assertEquals(
      400,
      call(
        "POST",
        "/api/qr/resolve",
        Map.of("payload", changed),
        token
      ).status()
    );
    changed = qr.deepCopy();
    changed.put("accountIdentifier", account(sender));
    assertEquals(
      400,
      call(
        "POST",
        "/api/qr/resolve",
        Map.of("payload", changed),
        token
      ).status()
    );
    changed = qr.deepCopy();
    changed.put("version", 2);
    assertEquals(
      400,
      call(
        "POST",
        "/api/qr/resolve",
        Map.of("payload", changed),
        token
      ).status()
    );
    changed = qr.deepCopy();
    changed.put("version", 1.5);
    assertEquals(
      400,
      call(
        "POST",
        "/api/qr/resolve",
        Map.of("payload", changed),
        token
      ).status()
    );
    changed = qr.deepCopy();
    changed.put("version", "1");
    assertEquals(
      400,
      call(
        "POST",
        "/api/qr/resolve",
        Map.of("payload", changed),
        token
      ).status()
    );
    changed = qr.deepCopy();
    changed.put("amount", 15);
    assertEquals(400, call("POST", "/api/qr/resolve", Map.of("payload", changed), token).status());
    changed = qr.deepCopy();
    changed.remove("amount");
    assertEquals(400, call("POST", "/api/qr/resolve", Map.of("payload", changed), token).status());
    changed = qr.deepCopy();
    changed.put("extra", "unsupported");
    assertEquals(400, call("POST", "/api/qr/resolve", Map.of("payload", changed), token).status());
    var payload = Map.of(
      "receiverAccount",
      account(receiver),
      "amount",
      "15.00",
      "qrReference",
      qr.path("reference").asText()
    );
    assertEquals(
      200,
      call("POST", "/api/transfers", payload, token, key()).status()
    );
    assertEquals(
      409,
      call("POST", "/api/transfers", payload, token, key()).status()
    );
    assertEquals(85, balance(token), 0.001);
  }

  @Test
  void expiredQrCannotBePaidAndDepositQrIsClearlyTyped() throws Exception {
    var user = register(mobile(), "QR User");
    String token = token(user);
    var qr = call(
      "POST",
      "/api/qr/requests",
      Map.of("purpose", "DEPOSIT"),
      token
    );
    assertEquals(201, qr.status());
    assertEquals("DEPOSIT", qr.body().path("purpose").asText());
    assertTrue(qr.body().path("amount").isNull());
    try (
      var conn = DBConnection.getConnection();
      var ps = conn.prepareStatement(
        "UPDATE qr_payment_requests SET expires_at=NOW()-INTERVAL '1 minute' WHERE reference=?"
      )
    ) {
      ps.setObject(1, UUID.fromString(qr.body().path("reference").asText()));
      ps.executeUpdate();
    }
    assertEquals(
      400,
      call(
        "POST",
        "/api/qr/resolve",
        Map.of("payload", qr.body()),
        token
      ).status()
    );
  }

  @Test
  void concurrentDepositCompletionsWithDifferentKeysCreditOnce()
    throws Exception {
    var user = register(mobile(), "Concurrent Deposit");
    String token = token(user);
    var deposit = call(
      "POST",
      "/api/deposits/demo",
      Map.of("amount", "80.00", "method", "GCASH_DEMO"),
      token,
      key()
    );
    assertEquals(201, deposit.status());
    String path =
      "/api/deposits/demo/" +
      deposit.body().path("reference").asText() +
      "/complete";
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> call("POST", path, Map.of(), token, key()));
      var second = pool.submit(() ->
        call("POST", path, Map.of(), token, key())
      );
      var a = first.get(30, TimeUnit.SECONDS);
      var b = second.get(30, TimeUnit.SECONDS);
      assertEquals(200, a.status());
      assertEquals(200, b.status());
      assertEquals(a.body(), b.body());
    }
    assertEquals(80, balance(token), 0.001);
    assertEquals(
      1,
      call("GET", "/api/transactions", null, token).body().size()
    );
  }

  @Test
  void concurrentDifferentKeyPaymentsConsumeOneQrOnce() throws Exception {
    var sender = register(mobile(), "Concurrent Sender");
    var receiver = register(mobile(), "Concurrent Receiver");
    String token = token(sender);
    fund(token, "100.00");
    var qr = call(
      "POST",
      "/api/qr/requests",
      Map.of("purpose", "TRANSFER", "amount", "20.00"),
      token(receiver)
    );
    assertEquals(201, qr.status());
    var payload = Map.of(
      "receiverAccount",
      account(receiver),
      "amount",
      "20.00",
      "qrReference",
      qr.body().path("reference").asText()
    );
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() ->
        call("POST", "/api/transfers", payload, token, key())
      );
      var second = pool.submit(() ->
        call("POST", "/api/transfers", payload, token, key())
      );
      var statuses = new ArrayList<>(
        List.of(
          first.get(30, TimeUnit.SECONDS).status(),
          second.get(30, TimeUnit.SECONDS).status()
        )
      );
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
    }
    assertEquals(80, balance(token), 0.001);
    assertEquals(20, balance(token(receiver)), 0.001);
  }

  @Test
  void oppositeDirectionTransfersDoNotDeadlockOrLoseBalances()
    throws Exception {
    var firstUser = register(mobile(), "First Sender");
    var secondUser = register(mobile(), "Second Sender");
    String firstToken = token(firstUser),
      secondToken = token(secondUser);
    fund(firstToken, "100.00");
    fund(secondToken, "100.00");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() ->
        call(
          "POST",
          "/api/transfers",
          Map.of("receiverAccount", account(secondUser), "amount", "30.00"),
          firstToken,
          key()
        )
      );
      var second = pool.submit(() ->
        call(
          "POST",
          "/api/transfers",
          Map.of("receiverAccount", account(firstUser), "amount", "10.00"),
          secondToken,
          key()
        )
      );
      assertEquals(200, first.get(30, TimeUnit.SECONDS).status());
      assertEquals(200, second.get(30, TimeUnit.SECONDS).status());
    }
    assertEquals(80, balance(firstToken), 0.001);
    assertEquals(120, balance(secondToken), 0.001);
  }

  @Test
  void newEndpointsRequireAuthAndHistoryIsImmutable() throws Exception {
    assertEquals(
      401,
      call(
        "POST",
        "/api/deposits/demo",
        Map.of("amount", "10.00", "method", "GCASH_DEMO"),
        null,
        key()
      ).status()
    );
    assertEquals(
      401,
      call(
        "POST",
        "/api/qr/requests",
        Map.of("purpose", "TRANSFER"),
        null
      ).status()
    );
    var user = register(mobile(), "History User");
    String token = token(user);
    fund(token, "10.00");
    assertEquals(
      "COMPLETED",
      call("GET", "/api/transactions", null, token)
        .body()
        .get(0)
        .path("status")
        .asText()
    );
    try (
      var conn = DBConnection.getConnection();
      var ps = conn.prepareStatement(
        "UPDATE transactions SET details='Changed' WHERE user_id=?"
      )
    ) {
      ps.setLong(1, user.body().path("user").path("id").asLong());
      assertThrows(SQLException.class, ps::executeUpdate);
    }
  }
}
