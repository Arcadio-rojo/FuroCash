package com.furocash.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
  named = "integration",
  matches = "true"
)
class CardProfileIntegrationTest extends DemoIntegrationTestSupport {

  @Test
  void profilePersistsEditableFieldsAndKeepsVerifiedPhone() throws Exception {
    String number = mobile();
    var registration = register(number, "Original Name");
    String token = token(registration);
    var initial = call("GET", "/api/users/me", null, token);
    assertEquals(200, initial.status());
    assertEquals(number, initial.body().path("mobileNumber").asText());
    assertTrue(
      initial.body().path("accountIdentifier").asText().matches("FC-\\d{10,}")
    );
    assertFalse(initial.body().path("createdAt").isNull());
    assertFalse(initial.body().has("pinHash"));

    var edited = call(
      "PATCH",
      "/api/users/me",
      Map.of(
        "fullName",
        "Updated Name",
        "email",
        "demo@example.com",
        "mobileNumber",
        "09999999999"
      ),
      token
    );
    assertEquals(200, edited.status(), edited.body().toString());
    assertEquals("Updated Name", edited.body().path("fullName").asText());
    assertEquals("demo@example.com", edited.body().path("email").asText());
    assertEquals(number, edited.body().path("mobileNumber").asText());
    assertEquals(
      initial.body().path("createdAt"),
      edited.body().path("createdAt")
    );
    assertEquals(
      "demo@example.com",
      call("GET", "/api/users/me", null, token).body().path("email").asText()
    );
    assertEquals(
      400,
      call(
        "PATCH",
        "/api/users/me",
        Map.of("fullName", " ", "email", "a@b.com"),
        token
      ).status()
    );
    assertEquals(
      400,
      call(
        "PATCH",
        "/api/users/me",
        Map.of("fullName", "Valid Name", "email", "invalid"),
        token
      ).status()
    );
    assertEquals(
      400,
      call(
        "PATCH",
        "/api/users/me",
        Map.of(
          "fullName",
          "Valid Name",
          "email",
          "demo" + (char) 0 + "@example.com"
        ),
        token
      ).status()
    );
    assertEquals(
      400,
      call(
        "PATCH",
        "/api/users/me",
        Map.of("fullName", "Invalid\nName", "email", "demo@example.com"),
        token
      ).status()
    );
    Map<String, Object> clear = new HashMap<>();
    clear.put("fullName", "Updated Name");
    clear.put("email", null);
    assertTrue(
      call("PATCH", "/api/users/me", clear, token).body().path("email").isNull()
    );
    assertEquals(401, call("GET", "/api/users/me", null, null).status());
  }

  @Test
  void oneOwnedCardUsesAccountBalanceAndRecordsIdempotentPurchases()
    throws Exception {
    String owner = token(register(mobile(), "Card Owner"));
    String other = token(register(mobile(), "Other User"));
    fund(owner, "500.00");
    var empty = call("GET", "/api/cards/my", null, owner);
    assertEquals(200, empty.status());
    assertTrue(empty.body().isNull());
    var created = call("POST", "/api/cards", Map.of(), owner);
    assertEquals(201, created.status(), created.body().toString());
    String id = created.body().path("id").asText();
    String path = "/api/cards/" + id;
    assertTrue(
      created.body().path("identifier").asText().startsWith("DEMO-FC-")
    );
    assertNotEquals(
      created.body().path("identifier").asText(),
      created.body().path("maskedIdentifier").asText()
    );
    assertEquals("Card Owner", created.body().path("cardholderName").asText());
    assertEquals("ACTIVE", created.body().path("status").asText());
    assertEquals(409, call("POST", "/api/cards", Map.of(), owner).status());
    assertEquals(
      404,
      call("PATCH", path + "/freeze", Map.of(), other).status()
    );
    assertEquals(
      404,
      call("PATCH", path + "/unfreeze", Map.of(), other).status()
    );
    assertEquals(
      404,
      call("PATCH", path + "/limit", Map.of("dailyLimit", "1"), other).status()
    );
    assertEquals(
      404,
      call("GET", path + "/transactions", null, other).status()
    );
    assertEquals(
      404,
      call(
        "POST",
        path + "/simulate-purchase",
        Map.of("amount", "1", "merchant", "Demo shop"),
        other,
        key()
      ).status()
    );
    assertEquals(
      200,
      call(
        "PATCH",
        path + "/limit",
        Map.of("dailyLimit", "100.00"),
        owner
      ).status()
    );
    assertEquals(
      400,
      call("PATCH", path + "/limit", Map.of("dailyLimit", "0"), owner).status()
    );
    assertEquals(
      400,
      call(
        "PATCH",
        path + "/limit",
        Map.of("dailyLimit", "1.001"),
        owner
      ).status()
    );
    assertEquals(
      400,
      call(
        "PATCH",
        path + "/limit",
        Map.of("dailyLimit", "1000000000"),
        owner
      ).status()
    );
    assertEquals(
      "FROZEN",
      call("PATCH", path + "/freeze", Map.of(), owner)
        .body()
        .path("status")
        .asText()
    );
    var purchase = Map.of("amount", "60.00", "merchant", "Demo shop");
    assertEquals(
      409,
      call("POST", path + "/simulate-purchase", purchase, owner, key()).status()
    );
    assertEquals(500.0, balance(owner));
    assertEquals(
      "ACTIVE",
      call("PATCH", path + "/unfreeze", Map.of(), owner)
        .body()
        .path("status")
        .asText()
    );
    String requestKey = key();
    var paid = call(
      "POST",
      path + "/simulate-purchase",
      purchase,
      owner,
      requestKey
    );
    assertEquals(200, paid.status(), paid.body().toString());
    assertEquals("COMPLETED", paid.body().path("status").asText());
    assertEquals(440.0, paid.body().path("balance").asDouble());
    assertEquals(
      paid.body(),
      call(
        "POST",
        path + "/simulate-purchase",
        purchase,
        owner,
        requestKey
      ).body()
    );
    assertEquals(
      409,
      call(
        "POST",
        path + "/simulate-purchase",
        Map.of("amount", "59", "merchant", "Demo shop"),
        owner,
        requestKey
      ).status()
    );
    assertEquals(
      422,
      call(
        "POST",
        path + "/simulate-purchase",
        Map.of("amount", "41", "merchant", "Demo shop"),
        owner,
        key()
      ).status()
    );
    assertEquals(
      400,
      call(
        "POST",
        path + "/simulate-purchase",
        Map.of("amount", "1.001", "merchant", "Demo shop"),
        owner,
        key()
      ).status()
    );
    assertEquals(
      400,
      call("POST", path + "/simulate-purchase", purchase, owner).status()
    );
    var history = call("GET", path + "/transactions", null, owner);
    assertEquals(1, history.body().size());
    assertEquals("CARD_PURCHASE", history.body().get(0).path("type").asText());
    assertEquals(id, history.body().get(0).path("cardId").asText());
    assertEquals("COMPLETED", history.body().get(0).path("status").asText());
    assertEquals(
      60.0,
      call("GET", "/api/cards/my", null, owner)
        .body()
        .path("spentToday")
        .asDouble()
    );
    assertEquals(440.0, balance(owner));
  }

  @Test
  void cardCannotSpendMoreThanAccountBalance() throws Exception {
    String owner = token(register(mobile(), "Empty Wallet"));
    var created = call("POST", "/api/cards", Map.of(), owner);
    assertEquals(201, created.status());
    String id = created.body().path("id").asText();
    String path = "/api/cards/" + id;
    assertEquals(
      422,
      call(
        "POST",
        path + "/simulate-purchase",
        Map.of("amount", "1", "merchant", "Demo shop"),
        owner,
        key()
      ).status()
    );
    assertEquals(0.0, balance(owner));
    assertEquals(
      0,
      call("GET", path + "/transactions", null, owner)
        .body()
        .size()
    );
  }

  @Test
  void concurrentPurchasesCannotExceedDailyLimit() throws Exception {
    String owner = token(register(mobile(), "Limit Owner"));
    fund(owner, "500.00");
    var created = call("POST", "/api/cards", Map.of(), owner);
    assertEquals(201, created.status());
    String id = created.body().path("id").asText();
    String path = "/api/cards/" + id;
    assertEquals(
      200,
      call(
        "PATCH",
        path + "/limit",
        Map.of("dailyLimit", "100"),
        owner
      ).status()
    );
    var first = CompletableFuture.supplyAsync(() -> buy(path, owner));
    var second = CompletableFuture.supplyAsync(() -> buy(path, owner));
    assertEquals(
      java.util.List.of(200, 422),
      java.util.stream.Stream.of(first.get().status(), second.get().status())
        .sorted()
        .toList()
    );
    assertEquals(440.0, balance(owner));
    assertEquals(
      1,
      call("GET", path + "/transactions", null, owner)
        .body()
        .size()
    );
  }

  @Test
  void concurrentPurchaseRetriesDebitOnceAndFailedKeyCanBeRetried()
    throws Exception {
    String owner = token(register(mobile(), "Retry Owner"));
    fund(owner, "200.00");
    var created = call("POST", "/api/cards", Map.of(), owner);
    assertEquals(201, created.status());
    String path = "/api/cards/" + created.body().path("id").asText();
    var purchase = Map.of("amount", "60", "merchant", "Retry demo");
    String requestKey = key();
    assertEquals(
      200,
      call("PATCH", path + "/freeze", Map.of(), owner).status()
    );
    assertEquals(
      409,
      call(
        "POST",
        path + "/simulate-purchase",
        purchase,
        owner,
        requestKey
      ).status()
    );
    assertEquals(
      200,
      call("PATCH", path + "/unfreeze", Map.of(), owner).status()
    );
    var first = CompletableFuture.supplyAsync(() ->
      buy(path, owner, purchase, requestKey)
    );
    var second = CompletableFuture.supplyAsync(() ->
      buy(path, owner, purchase, requestKey)
    );
    var firstReply = first.get();
    var secondReply = second.get();
    assertEquals(200, firstReply.status(), firstReply.body().toString());
    assertEquals(200, secondReply.status(), secondReply.body().toString());
    assertEquals(firstReply.body(), secondReply.body());
    assertEquals(140.0, balance(owner));
    assertEquals(
      1,
      call("GET", path + "/transactions", null, owner)
        .body()
        .size()
    );
    assertEquals(
      60.0,
      call("GET", "/api/cards/my", null, owner)
        .body()
        .path("spentToday")
        .asDouble()
    );
  }

  private static Reply buy(String path, String token) {
    return buy(
      path,
      token,
      Map.of("amount", "60", "merchant", "Concurrent demo"),
      key()
    );
  }

  private static Reply buy(
    String path,
    String token,
    Map<String, String> purchase,
    String requestKey
  ) {
    try {
      return call(
        "POST",
        path + "/simulate-purchase",
        purchase,
        token,
        requestKey
      );
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
