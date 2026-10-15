package com.furocash.sms;

import com.furocash.exception.SmsSendException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class SemaphoreSmsSender implements SmsSender {

  private static final URI ENDPOINT = URI.create("https://api.semaphore.co/api/v4/messages");

  private final HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .build();
  private final String apiKey;
  private final String senderName;

  public SemaphoreSmsSender(String apiKey, String senderName) {
    this.apiKey = apiKey;
    this.senderName = senderName;
  }

  @Override
  public void send(String mobile, String message) {
    StringBuilder form = new StringBuilder();
    form.append("apikey=").append(enc(apiKey))
            .append("&number=").append(enc(mobile))
            .append("&message=").append(enc(message));
    if (senderName != null && !senderName.isBlank()) {
      form.append("&sendername=").append(enc(senderName));
    }

    HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
            .build();

    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new SmsSendException("The SMS provider rejected the request (HTTP " + response.statusCode() + ")");
      }
    } catch (IOException e) {
      throw new SmsSendException("Could not reach the SMS provider", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SmsSendException("The SMS request was interrupted", e);
    }
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}