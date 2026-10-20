package com.furocash.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.furocash.exception.SmsSendException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

public class SemaphoreSmsSender implements SmsSender {

  private static final URI ENDPOINT = URI.create(
    "https://api.semaphore.co/api/v4/messages"
  );

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> ACCEPTED_STATUSES = Set.of(
    "queued",
    "pending",
    "sent"
  );

  private final HttpClient client;
  private final URI endpoint;
  private final String apiKey;
  private final String senderName;

  public SemaphoreSmsSender(String apiKey, String senderName) {
    this(
      apiKey,
      senderName,
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
      ENDPOINT
    );
  }

  SemaphoreSmsSender(
    String apiKey,
    String senderName,
    HttpClient client,
    URI endpoint
  ) {
    this.apiKey = apiKey;
    this.senderName = senderName;
    this.client = client;
    this.endpoint = endpoint;
  }

  @Override
  public void send(String mobile, String message) {
    StringBuilder form = new StringBuilder();
    form
      .append("apikey=")
      .append(enc(apiKey))
      .append("&number=")
      .append(enc(mobile))
      .append("&message=")
      .append(enc(message));
    if (senderName != null && !senderName.isBlank()) {
      form.append("&sendername=").append(enc(senderName));
    }

    HttpRequest request = HttpRequest.newBuilder(endpoint)
      .timeout(Duration.ofSeconds(15))
      .header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
      .build();

    try {
      HttpResponse<String> response = client.send(
        request,
        HttpResponse.BodyHandlers.ofString()
      );
      if (response.statusCode() == 429) {
        throw new SmsSendException(
          "The SMS provider is busy. Please wait a moment before requesting another code."
        );
      }
      if (response.statusCode() / 100 != 2) {
        throw new SmsSendException(
          "The SMS provider could not send the code (HTTP " +
            response.statusCode() +
            "). Please contact support if this continues."
        );
      }
      validateAcceptedResponse(response.body());
    } catch (IOException e) {
      throw new SmsSendException("Could not reach the SMS provider", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SmsSendException("The SMS request was interrupted", e);
    }
  }

  private static void validateAcceptedResponse(String body) {
    try {
      JsonNode messages = JSON.readTree(body);
      if (
        messages == null || !messages.isArray() || messages.size() != 1
      ) throw invalidResponse();
      JsonNode entry = messages.get(0);
      JsonNode messageId = entry.get("message_id");
      if (
        messageId == null ||
        !messageId.isIntegralNumber() ||
        messageId.asLong() <= 0 ||
        !ACCEPTED_STATUSES.contains(
          entry.path("status").asText().toLowerCase(Locale.ROOT)
        )
      ) {
        throw invalidResponse();
      }
    } catch (IOException e) {
      // Never expose the provider's response: it may contain the API key, mobile, or OTP.
      throw invalidResponse();
    }
  }

  private static SmsSendException invalidResponse() {
    return new SmsSendException(
      "The SMS provider did not accept the message. Please contact support to check the SMS account, credits, and active sender name."
    );
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
