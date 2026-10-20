package com.furocash.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.furocash.exception.SmsSendException;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** PhilSMS v3 bearer-authenticated JSON transport. OTP creation/verification stays in OtpService. */
public final class PhilSmsSender implements SmsSender {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> HOSTS = Set.of(
    "dashboard.philsms.com",
    "app.philsms.com"
  );
  private static final Set<String> REJECTED = Set.of(
    "failed",
    "undelivered",
    "rejected",
    "expired",
    "cancelled",
    "canceled",
    "error"
  );
  private final String token, senderId;
  private final HttpClient client;
  private final URI endpoint;

  public PhilSmsSender(String token, String senderId, String baseUrl) {
    this(
      token,
      senderId,
      HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
      endpoint(baseUrl)
    );
  }

  PhilSmsSender(
    String token,
    String senderId,
    HttpClient client,
    URI endpoint
  ) {
    this.token = token;
    this.senderId = senderId;
    this.client = client;
    this.endpoint = endpoint;
  }

  private static URI endpoint(String baseUrl) {
    URI base = URI.create(baseUrl);
    if (
      !"https".equalsIgnoreCase(base.getScheme()) ||
      base.getHost() == null ||
      !HOSTS.contains(base.getHost().toLowerCase(Locale.ROOT)) ||
      (base.getPort() != -1 && base.getPort() != 443) ||
      base.getUserInfo() != null ||
      base.getQuery() != null ||
      base.getFragment() != null ||
      !("/api/v3/".equals(base.getPath()) || "/api/v3".equals(base.getPath()))
    ) {
      throw new IllegalArgumentException(
        "PHILSMS_API_BASE_URL must be an HTTPS PhilSMS /api/v3/ endpoint"
      );
    }
    return URI.create(base.toString().replaceAll("/$", "") + "/sms/send");
  }

  @Override
  public void send(String mobile, String message) {
    String recipient = recipient(mobile);
    if (message == null || message.isBlank()) throw new SmsSendException(
      "The SMS message is empty"
    );
    try {
      String body = JSON.writeValueAsString(
        Map.of(
          "recipient",
          recipient,
          "sender_id",
          senderId,
          "type",
          "plain",
          "message",
          message
        )
      );
      HttpRequest request = HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofSeconds(15))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
      HttpResponse<String> response = client.send(
        request,
        HttpResponse.BodyHandlers.ofString()
      );
      if (response.statusCode() == 429) throw new SmsSendException(
        "The SMS provider is busy. Please wait a moment before requesting another code."
      );
      if (response.statusCode() / 100 != 2) throw new SmsSendException(
        "PhilSMS could not send the code (HTTP " +
          response.statusCode() +
          "). Please contact support if this continues."
      );
      validateAccepted(response.body());
    } catch (IOException e) {
      throw new SmsSendException(
        "Could not reach PhilSMS. Please try again later",
        e
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SmsSendException("The SMS request was interrupted", e);
    }
  }

  private static String recipient(String mobile) {
    if (mobile != null) {
      String number = mobile.trim();
      if (number.matches("09[0-9]{9}")) return "63" + number.substring(1);
      if (number.matches("\\+?639[0-9]{9}")) return number.replace("+", "");
    }
    throw new SmsSendException("Enter one valid Philippine mobile number");
  }

  private static void validateAccepted(String body) {
    try {
      JsonNode result = JSON.readTree(body);
      if (
        result == null ||
        !result.isObject() ||
        !"success".equalsIgnoreCase(result.path("status").asText())
      ) throw rejected();
      JsonNode data = result.path("data");
      if (data.isArray()) {
        for (JsonNode entry : data) checkDeliveryStatus(entry);
      } else checkDeliveryStatus(data);
    } catch (IOException e) {
      // Responses may contain the OTP or token. Never echo or log their raw body.
      throw rejected();
    }
  }

  private static void checkDeliveryStatus(JsonNode data) {
    if (
      REJECTED.contains(data.path("status").asText().toLowerCase(Locale.ROOT))
    ) throw rejected();
  }

  private static SmsSendException rejected() {
    return new SmsSendException(
      "PhilSMS did not accept the message. Please contact support to check the API token, SMS credits, and enabled Sender ID."
    );
  }
}
