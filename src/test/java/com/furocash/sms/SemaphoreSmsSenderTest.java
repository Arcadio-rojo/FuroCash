package com.furocash.sms;

import static org.junit.jupiter.api.Assertions.*;

import com.furocash.exception.SmsSendException;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

class SemaphoreSmsSenderTest {

  HttpServer server;
  SemaphoreSmsSender sender;
  String response;
  int status;
  Map<String, String> request;

  @BeforeEach
  void start() throws Exception {
    status = 200;
    response =
      "[{\"message_id\":12345,\"recipient\":\"639171234567\",\"status\":\"Queued\"}]";
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/messages", exchange -> {
      request = new HashMap<>();
      for (String pair : new String(
        exchange.getRequestBody().readAllBytes(),
        StandardCharsets.UTF_8
      ).split("&")) {
        String[] parts = pair.split("=", 2);
        request.put(
          URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
          URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
        );
      }
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    sender = new SemaphoreSmsSender(
      "test-key-only",
      "FuroCash",
      HttpClient.newHttpClient(),
      URI.create(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/messages"
      )
    );
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @Test
  void acceptedMessageSubmitsTheActualOtpAndRecipient() {
    sender.send("09171234567", "Your FuroCash registration code is 123456.");
    assertEquals(
      Map.of(
        "apikey",
        "test-key-only",
        "number",
        "09171234567",
        "message",
        "Your FuroCash registration code is 123456.",
        "sendername",
        "FuroCash"
      ),
      request
    );
  }

  @Test
  void pendingAndSentMessagesAreAccepted() {
    for (String deliveryStatus : List.of("Pending", "Sent")) {
      response =
        "[{\"message_id\":12345,\"recipient\":\"639171234567\",\"status\":\"" +
        deliveryStatus +
        "\"}]";
      assertDoesNotThrow(() ->
        sender.send("09171234567", "Your code is 123456")
      );
    }
  }

  @Test
  void failedNetworkDeliveryDoesNotPretendTheCodeWasSent() {
    response = "[{\"message_id\":12345,\"status\":\"Failed\"}]";
    assertThrows(SmsSendException.class, () ->
      sender.send("09171234567", "Your code is 123456")
    );
  }

  @Test
  void refundedAndUnknownStatusesAreRejected() {
    for (String deliveryStatus : List.of("Refunded", "unknown", "")) {
      response =
        "[{\"message_id\":12345,\"status\":\"" + deliveryStatus + "\"}]";
      assertThrows(SmsSendException.class, () ->
        sender.send("09171234567", "Your code is 123456")
      );
    }
  }

  @Test
  void errorObjectAndInvalidResponsesAreRejected() {
    for (String body : List.of(
      "{\"apikey\":[\"Invalid API key test-key-only\"]}",
      "[]",
      "null",
      "<html>upstream failure</html>",
      "[{\"status\":\"Queued\"}]"
    )) {
      response = body;
      var error = assertThrows(SmsSendException.class, () ->
        sender.send("09171234567", "Your code is 123456")
      );
      assertFalse(error.getMessage().contains("test-key-only"));
      assertFalse(error.getMessage().contains("123456"));
    }
  }

  @Test
  void unauthorizedProviderResponseIsSanitized() {
    status = 401;
    response = "test-key-only 123456";
    var error = assertThrows(SmsSendException.class, () ->
      sender.send("09171234567", "Your code is 123456")
    );
    assertFalse(error.getMessage().contains("test-key-only"));
    assertFalse(error.getMessage().contains("123456"));
  }

  @Test
  void throttlingIsReportedWithoutAnAutomaticDuplicateSend() {
    status = 429;
    assertThrows(SmsSendException.class, () ->
      sender.send("09171234567", "Your code is 123456")
    );
  }
}
