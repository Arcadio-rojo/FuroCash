package com.furocash.sms;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.furocash.exception.SmsSendException;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

class PhilSmsSenderTest {

  static final ObjectMapper JSON = new ObjectMapper();
  HttpServer server;
  PhilSmsSender sender;
  volatile String response;
  volatile int status;
  volatile JsonNode request;
  volatile String authorization, accept, contentType, method;

  @BeforeEach
  void start() throws Exception {
    status = 200;
    response =
      "{\"status\":\"success\",\"data\":{\"uid\":\"message-123\",\"status\":\"Queued\"}}";
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v3/sms/send", exchange -> {
      authorization = exchange.getRequestHeaders().getFirst("Authorization");
      accept = exchange.getRequestHeaders().getFirst("Accept");
      contentType = exchange.getRequestHeaders().getFirst("Content-Type");
      method = exchange.getRequestMethod();
      request = JSON.readTree(exchange.getRequestBody().readAllBytes());
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    sender = new PhilSmsSender(
      "test-token-only",
      "PhilSMS",
      HttpClient.newHttpClient(),
      URI.create(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v3/sms/send"
      )
    );
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @Test
  void sendsBearerAuthenticatedJsonAndNormalizesTheRecipient()
    throws Exception {
    sender.send("09171234567", "Your FuroCash registration code is 123456.");
    assertEquals("POST", method);
    assertEquals("Bearer test-token-only", authorization);
    assertEquals("application/json", accept);
    assertEquals("application/json", contentType);
    assertEquals(
      JSON.readTree(
        "{\"recipient\":\"639171234567\",\"sender_id\":\"PhilSMS\",\"type\":\"plain\",\"message\":\"Your FuroCash registration code is 123456.\"}"
      ),
      request
    );
  }

  @Test
  void alreadyInternationalNumbersDoNotGetADuplicateCountryCode() {
    for (String number : List.of("639171234567", "+639171234567")) {
      sender.send(number, "Your code is 123456");
      assertEquals("639171234567", request.get("recipient").asText());
    }
  }

  @Test
  void cannotSendToMultipleOrInvalidNumbers() {
    for (String number : List.of(
      "09171234567,09181234567",
      "not-a-number",
      "08171234567"
    ))
      assertThrows(SmsSendException.class, () ->
        sender.send(number, "Your code is 123456")
      );
    assertNull(request);
  }

  @Test
  void providerErrorInsideHttp200IsNotSuccess() {
    response =
      "{\"status\":\"error\",\"message\":\"Insufficient balance test-token-only 123456\"}";
    var error = assertThrows(SmsSendException.class, () ->
      sender.send("09171234567", "Your code is 123456")
    );
    assertFalse(error.getMessage().contains("test-token-only"));
    assertFalse(error.getMessage().contains("123456"));
  }

  @Test
  void failedMessageInsideSuccessEnvelopeIsRejected() {
    response = "{\"status\":\"success\",\"data\":{\"status\":\"Failed\"}}";
    assertThrows(SmsSendException.class, () ->
      sender.send("09171234567", "Your code is 123456")
    );
  }

  @Test
  void malformedAndEmptyResponsesCannotClaimDelivery() {
    for (String body : List.of(
      "<html>error</html>",
      "null",
      "[]",
      "{}",
      "{\"status\":\"unknown\"}"
    )) {
      response = body;
      assertThrows(SmsSendException.class, () ->
        sender.send("09171234567", "Your code is 123456")
      );
    }
  }

  @Test
  void unauthorizedAndThrottledResponsesAreErrors() {
    for (int code : List.of(401, 403, 429, 500, 302)) {
      status = code;
      assertThrows(SmsSendException.class, () ->
        sender.send("09171234567", "Your code is 123456")
      );
    }
  }

  @Test
  void productionEndpointMustBeAnHttpsPhilSmsEndpoint() {
    assertDoesNotThrow(() ->
      new PhilSmsSender(
        "test-token-only",
        "PhilSMS",
        "https://dashboard.philsms.com/api/v3/"
      )
    );
    for (String base : List.of(
      "http://dashboard.philsms.com/api/v3/",
      "https://example.com/api/v3/",
      "https://dashboard.philsms.com/api/v3/?token=secret"
    ))
      assertThrows(IllegalArgumentException.class, () ->
        new PhilSmsSender("test-token-only", "PhilSMS", base)
      );
  }
}
