package com.furocash.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.furocash.Main;
import com.furocash.util.*;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;

/** Opt-in real PostgreSQL tests, always inside a newly created disposable schema. */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
  named = "integration",
  matches = "true"
)
class BankingIntegrationTest {

  static final ObjectMapper json = new ObjectMapper();
  static final HttpClient client = HttpClient.newHttpClient();
  static final Map<String, String> codes = new ConcurrentHashMap<>();
  static final AtomicLong next = new AtomicLong(10000000);
  static Javalin app;
  static String baseUrl, dbUrl, dbUser, dbPass, schema;

  record Reply(int status, JsonNode body) {}

  static String mobile() {
    return "09" + String.format("%09d", next.incrementAndGet());
  }

  @BeforeAll
  static void start() throws Exception {
    dbUrl = AppConfig.get(
      "TEST_DB_URL",
      AppConfig.get(
        "DB_URL",
        "jdbc:postgresql://localhost:5433/" +
          AppConfig.get("DB_NAME", "furocash")
      )
    );
    dbUser = AppConfig.get("DB_USER", "furocash");
    dbPass = AppConfig.get("DB_PASSWORD", "");
    schema = "furocash_test_" + UUID.randomUUID().toString().replace("-", "");
    try (
      Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
      Statement statement = conn.createStatement()
    ) {
      statement.execute("CREATE SCHEMA " + schema);
    }
    System.setProperty(
      "DB_URL",
      dbUrl + (dbUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema
    );
    System.setProperty(
      "JWT_SECRET",
      "integration-test-secret-at-least-32-characters"
    );
    System.setProperty("DEMO_CASH_IN", "true");
    DBConnection.migrate();
    app = Main.createApp((number, message) -> {
      var matcher = Pattern.compile("code is ([0-9]{6})").matcher(message);
      assertTrue(matcher.find());
      codes.put(
        number + (message.contains("PIN reset") ? ":RESET_PIN" : ":REGISTER"),
        matcher.group(1)
      );
    }).start(0);
    baseUrl = "http://localhost:" + app.port();
  }

  @AfterAll
  static void stop() throws Exception {
    if (app != null) app.stop();
    if (schema != null) try (
      Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
      Statement statement = conn.createStatement()
    ) {
      statement.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  static Reply call(String method, String path, Object body, String token)
    throws Exception {
    var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).header(
      "Content-Type",
      "application/json"
    );
    if (token != null) request.header("Authorization", "Bearer " + token);
    if ("POST".equals(method) && ("/api/transfers".equals(path) || "/api/account/cash-in".equals(path))) request.header("Idempotency-Key",UUID.randomUUID().toString());
    request.method(
      method,
      body == null
        ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))
    );
    var response = client.send(
      request.build(),
      HttpResponse.BodyHandlers.ofString()
    );
    return new Reply(response.statusCode(), json.readTree(response.body()));
  }

  static Reply post(String path, Object body) throws Exception {
    return call("POST", path, body, null);
  }

  static Reply register(String number) throws Exception {
    assertEquals(
      200,
      post(
        "/api/auth/register/request-code",
        Map.of("mobile", number, "pin", "1234", "fullName", "Demo User")
      ).status()
    );
    Reply result = post(
      "/api/auth/register",
      Map.of(
        "mobile",
        number,
        "pin",
        "1234",
        "fullName",
        "Demo User",
        "otp",
        codes.get(number + ":REGISTER")
      )
    );
    assertEquals(201, result.status(), result.body().toString());
    return result;
  }

  static void ageCode(String number, String purpose, boolean expired)
    throws Exception {
    try (
      Connection conn = DBConnection.getConnection();
      PreparedStatement ps = conn.prepareStatement(
        "UPDATE otp_codes SET last_sent_at=?,expires_at=? WHERE mobile_number=? AND purpose=?"
      )
    ) {
      ps.setTimestamp(
        1,
        Timestamp.valueOf(LocalDateTime.now().minusSeconds(61))
      );
      ps.setTimestamp(
        2,
        Timestamp.valueOf(
          expired
            ? LocalDateTime.now().minusSeconds(1)
            : LocalDateTime.now().plusMinutes(5)
        )
      );
      ps.setString(3, number);
      ps.setString(4, purpose);
      ps.executeUpdate();
    }
  }

  @Test
  void registrationLoginFundingTransferAndHistoryAreConnected()
    throws Exception {
    String sender = mobile(),
      receiver = mobile();
    String a = register(sender).body().get("token").asText(),
      b = register(receiver).body().get("token").asText();
    assertEquals(
      sender,
      call("GET", "/api/auth/me", null, a).body().get("mobileNumber").asText()
    );
    assertEquals(
      200,
      post("/api/auth/login", Map.of("mobile", sender, "pin", "1234")).status()
    );
    Reply funding = call(
      "POST",
      "/api/account/cash-in",
      Map.of("amount", "100.50"),
      a
    );
    assertEquals(200, funding.status());
    assertEquals(100.50, funding.body().get("balance").asDouble(), 0.001);
    Reply transfer = call(
      "POST",
      "/api/transfers",
      Map.of("receiverMobile", receiver, "amount", "25.25"),
      a
    );
    assertEquals(200, transfer.status());
    assertEquals(75.25, transfer.body().get("balance").asDouble(), 0.001);
    assertEquals(
      25.25,
      call("GET", "/api/account/balance", null, b)
        .body()
        .get("balance")
        .asDouble(),
      0.001
    );
    var history = call("GET", "/api/transactions?page=0&size=10", null, a);
    assertEquals(2, history.body().size());
    assertEquals("TRANSFER_OUT", history.body().get(0).get("type").asText());
    assertEquals(
      422,
      call(
        "POST",
        "/api/transfers",
        Map.of("receiverMobile", receiver, "amount", "999.00"),
        a
      ).status()
    );
    assertEquals(
      400,
      call(
        "POST",
        "/api/transfers",
        Map.of("receiverMobile", sender, "amount", "1.00"),
        a
      ).status()
    );
    assertEquals(
      400,
      call(
        "POST",
        "/api/account/cash-in",
        Map.of("amount", "1.001"),
        a
      ).status()
    );
    System.setProperty("DEMO_CASH_IN", "false");
    try {
      assertEquals(
        403,
        call(
          "POST",
          "/api/account/cash-in",
          Map.of("amount", "10.00"),
          a
        ).status()
      );
    } finally {
      System.setProperty("DEMO_CASH_IN", "true");
    }
  }

  @Test
  void wrongCodeLimitSurvivesAndCannotBeResetByImmediateResend()
    throws Exception {
    String number = mobile();
    post(
      "/api/auth/register/request-code",
      Map.of("mobile", number, "pin", "1234", "fullName", "Demo User")
    );
    String code = codes.get(number + ":REGISTER");
    String wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < 3; i++) assertEquals(
      400,
      post(
        "/api/auth/register",
        Map.of(
          "mobile",
          number,
          "pin",
          "1234",
          "fullName",
          "Demo User",
          "otp",
          wrong
        )
      ).status()
    );
    assertEquals(
      400,
      post(
        "/api/auth/register",
        Map.of(
          "mobile",
          number,
          "pin",
          "1234",
          "fullName",
          "Demo User",
          "otp",
          code
        )
      ).status()
    );
    assertEquals(
      429,
      post(
        "/api/auth/register/request-code",
        Map.of("mobile", number, "pin", "1234", "fullName", "Demo User")
      ).status()
    );
    ageCode(number, "REGISTER", false);
    assertEquals(
      200,
      post(
        "/api/auth/register/request-code",
        Map.of("mobile", number, "pin", "1234", "fullName", "Demo User")
      ).status()
    );
    assertEquals(
      201,
      post(
        "/api/auth/register",
        Map.of(
          "mobile",
          number,
          "pin",
          "1234",
          "fullName",
          "Demo User",
          "otp",
          codes.get(number + ":REGISTER")
        )
      ).status()
    );
  }

  @Test
  void resetRequiresRecoveryCodeAndRevokesPreviousTokens() throws Exception {
    String number = mobile();
    String token = register(number).body().get("token").asText();
    String registrationCode = codes.get(number + ":REGISTER");
    assertEquals(
      400,
      post(
        "/api/auth/reset-pin",
        Map.of("mobile", number, "otp", registrationCode, "pin", "5678")
      ).status()
    );
    assertEquals(
      200,
      post("/api/auth/forgot-pin", Map.of("mobile", number)).status()
    );
    String code = codes.get(number + ":RESET_PIN");
    assertEquals(
      200,
      post(
        "/api/auth/reset-pin",
        Map.of("mobile", number, "otp", code, "pin", "5678")
      ).status()
    );
    assertEquals(401, call("GET", "/api/auth/me", null, token).status());
    assertEquals(
      401,
      post("/api/auth/login", Map.of("mobile", number, "pin", "1234")).status()
    );
    assertEquals(
      200,
      post("/api/auth/login", Map.of("mobile", number, "pin", "5678")).status()
    );
    assertEquals(
      400,
      post(
        "/api/auth/reset-pin",
        Map.of("mobile", number, "otp", code, "pin", "9876")
      ).status()
    );
  }

  @Test
  void expiredOtpCannotRegister() throws Exception {
    String number = mobile();
    post(
      "/api/auth/register/request-code",
      Map.of("mobile", number, "pin", "1234", "fullName", "Demo User")
    );
    ageCode(number, "REGISTER", true);
    assertEquals(
      400,
      post(
        "/api/auth/register",
        Map.of(
          "mobile",
          number,
          "pin",
          "1234",
          "fullName",
          "Demo User",
          "otp",
          codes.get(number + ":REGISTER")
        )
      ).status()
    );
  }

  @Test
  void concurrentLoginsCannotLoseFailedAttemptCounts() throws Exception {
    String number = mobile();
    register(number);
    try (var pool = Executors.newFixedThreadPool(3)) {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < 3; i++) results.add(
        pool.submit(() ->
          post(
            "/api/auth/login",
            Map.of("mobile", number, "pin", "9999")
          ).status()
        )
      );
      List<Integer> statuses = new ArrayList<>();
      for (var result : results) statuses.add(result.get());
      Collections.sort(statuses);
      assertEquals(List.of(401, 401, 423), statuses);
    }
    assertEquals(
      423,
      post("/api/auth/login", Map.of("mobile", number, "pin", "1234")).status()
    );
  }

  @Test
  void concurrentRegistrationConsumesTheCodeOnce() throws Exception {
    String number = mobile();
    post(
      "/api/auth/register/request-code",
      Map.of("mobile", number, "pin", "1234", "fullName", "Demo User")
    );
    var payload = Map.of(
      "mobile",
      number,
      "pin",
      "1234",
      "fullName",
      "Demo User",
      "otp",
      codes.get(number + ":REGISTER")
    );
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() ->
        post("/api/auth/register", payload).status()
      );
      var second = pool.submit(() ->
        post("/api/auth/register", payload).status()
      );
      var statuses = new ArrayList<>(List.of(first.get(), second.get()));
      Collections.sort(statuses);
      assertEquals(List.of(201, 400), statuses);
    }
  }

  @Test
  void unknownRecoveryNumberReturnsGenericSuccessWithoutSms() throws Exception {
    String number = mobile();
    Reply response = post("/api/auth/forgot-pin", Map.of("mobile", number));
    assertEquals(200, response.status());
    assertFalse(codes.containsKey(number + ":RESET_PIN"));
  }

  @Test
  void failedSmsDoesNotCreateACooldownOrAnAccount() throws Exception {
    String number = mobile();
    var unavailable = new com.furocash.service.OtpService(
      (recipient, message) -> {
        throw new com.furocash.exception.SmsSendException(
          "Provider unavailable"
        );
      }
    );
    assertThrows(com.furocash.exception.SmsSendException.class, () ->
      unavailable.requestRegistrationCode(number, "1234", "Demo User")
    );
    assertEquals(
      401,
      post("/api/auth/login", Map.of("mobile", number, "pin", "1234")).status()
    );
    assertEquals(
      200,
      post(
        "/api/auth/register/request-code",
        Map.of("mobile", number, "pin", "1234", "fullName", "Demo User")
      ).status()
    );
    assertEquals(
      201,
      post(
        "/api/auth/register",
        Map.of(
          "mobile",
          number,
          "pin",
          "1234",
          "fullName",
          "Demo User",
          "otp",
          codes.get(number + ":REGISTER")
        )
      ).status()
    );
  }

  @Test
  void failedRecoverySmsLeavesTheExistingPinUnchangedAndAllowsRetry()
    throws Exception {
    String number = mobile();
    register(number);
    var unavailable = new com.furocash.service.OtpService(
      (recipient, message) -> {
        throw new com.furocash.exception.SmsSendException(
          "Provider unavailable"
        );
      }
    );
    assertThrows(com.furocash.exception.SmsSendException.class, () ->
      unavailable.requestResetCode(number)
    );
    assertEquals(
      200,
      post("/api/auth/login", Map.of("mobile", number, "pin", "1234")).status()
    );
    assertEquals(
      200,
      post("/api/auth/forgot-pin", Map.of("mobile", number)).status()
    );
    assertEquals(
      200,
      post(
        "/api/auth/reset-pin",
        Map.of(
          "mobile",
          number,
          "pin",
          "5678",
          "otp",
          codes.get(number + ":RESET_PIN")
        )
      ).status()
    );
  }
}
