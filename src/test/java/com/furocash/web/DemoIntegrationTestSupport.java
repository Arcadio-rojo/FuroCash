package com.furocash.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.furocash.Main;
import com.furocash.util.*;
import io.javalin.Javalin;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;
import org.junit.jupiter.api.*;

public abstract class DemoIntegrationTestSupport {

  protected static final ObjectMapper JSON = new ObjectMapper();
  protected static final HttpClient CLIENT = HttpClient.newHttpClient();
  protected static final Map<String, String> CODES = new ConcurrentHashMap<>();
  private static final AtomicLong NEXT = new AtomicLong(20000000);
  private static Javalin app;
  private static String dbUrl, dbUser, dbPass, schema;
  protected static String baseUrl;

  public record Reply(int status, JsonNode body) {}

  protected static String mobile() {
    return "09" + String.format("%09d", NEXT.incrementAndGet());
  }

  @BeforeAll
  static void startDemoServer() throws Exception {
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
    schema =
      "furocash_demo_test_" + UUID.randomUUID().toString().replace("-", "");
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
      "isolated-demo-integration-secret-at-least-32-characters"
    );
    System.setProperty("DEMO_CASH_IN", "true");
    DBConnection.migrate();
    app = Main.createApp((number, message) -> {
      var matcher = Pattern.compile("code is ([0-9]{6})").matcher(message);
      assertTrue(matcher.find());
      CODES.put(number, matcher.group(1));
    }).start(0);
    baseUrl = "http://localhost:" + app.port();
  }

  @AfterAll
  static void stopDemoServer() throws Exception {
    if (app != null) app.stop();
    if (schema != null) try (
      Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
      Statement statement = conn.createStatement()
    ) {
      statement.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  protected static Reply call(
    String method,
    String path,
    Object body,
    String token
  ) throws Exception {
    return call(method, path, body, token, null);
  }

  protected static Reply call(
    String method,
    String path,
    Object body,
    String token,
    String key
  ) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).header(
      "Content-Type",
      "application/json"
    );
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (key != null) request.header("Idempotency-Key", key);
    request.method(
      method,
      body == null
        ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))
    );
    var response = CLIENT.send(
      request.build(),
      HttpResponse.BodyHandlers.ofString()
    );
    return new Reply(response.statusCode(), JSON.readTree(response.body()));
  }

  protected static Reply register(String number, String name) throws Exception {
    assertEquals(
      200,
      call(
        "POST",
        "/api/auth/register/request-code",
        Map.of("mobile", number, "pin", "1234", "fullName", name),
        null
      ).status()
    );
    Reply result = call(
      "POST",
      "/api/auth/register",
      Map.of(
        "mobile",
        number,
        "pin",
        "1234",
        "fullName",
        name,
        "otp",
        CODES.get(number)
      ),
      null
    );
    assertEquals(201, result.status());
    return result;
  }

  protected static String token(Reply user) {
    return user.body().get("token").asText();
  }

  protected static String account(Reply user) {
    return String.format(
      "FC-%010d",
      user.body().path("user").path("id").asLong()
    );
  }

  protected static String key() {
    return UUID.randomUUID().toString();
  }

  protected static void fund(String token, String amount) throws Exception {
    assertEquals(
      200,
      call(
        "POST",
        "/api/account/cash-in",
        Map.of("amount", amount),
        token,
        key()
      ).status()
    );
  }

  protected static double balance(String token) throws Exception {
    return call("GET", "/api/account/balance", null, token)
      .body()
      .path("balance")
      .asDouble();
  }
}
