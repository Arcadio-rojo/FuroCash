package com.furocash.web;

import static org.junit.jupiter.api.Assertions.*;

import com.furocash.service.AuthService;
import com.furocash.service.OtpService;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;

class AuthRoutesTest {

  private static Javalin app;
  private static final HttpClient client = HttpClient.newHttpClient();

  @BeforeAll
  static void start() {
    app = Javalin.create();
    ApiExceptionHandler.mount(app);
    AuthFilter.mount(app);
    AuthRoutes.mount(
      app,
      new AuthService(),
      new OtpService((mobile, message) -> {})
    );
    app.start(0);
  }

  @AfterAll
  static void stop() {
    if (app != null) app.stop();
  }

  private HttpResponse<String> post(String path, String json) throws Exception {
    return client.send(
      HttpRequest.newBuilder(
        URI.create("http://localhost:" + app.port() + path)
      )
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
  }

  @Test
  void missingOtpIsAClientError() throws Exception {
    var response = post(
      "/api/auth/register",
      "{\"mobile\":\"09171234567\",\"pin\":\"1234\",\"fullName\":\"Test User\"}"
    );
    assertEquals(400, response.statusCode());
    assertTrue(response.body().contains("error"));
  }

  @Test
  void resetRejectsMalformedCodeWithoutChangingAnything() throws Exception {
    assertEquals(
      400,
      post(
        "/api/auth/reset-pin",
        "{\"mobile\":\"09171234567\",\"otp\":\"bad\",\"pin\":\"5678\"}"
      ).statusCode()
    );
  }

  @Test
  void currentUserRequiresAuthentication() throws Exception {
    var response = client.send(
      HttpRequest.newBuilder(
        URI.create("http://localhost:" + app.port() + "/api/auth/me")
      )
        .GET()
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    assertEquals(401, response.statusCode());
  }
}
