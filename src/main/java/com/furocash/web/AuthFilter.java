package com.furocash.web;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.furocash.exception.AuthenticationRequiredException;
import com.furocash.service.AuthService;
import com.furocash.util.JwtUtil;
import io.javalin.Javalin;
import io.javalin.http.*;

public final class AuthFilter {

  private AuthFilter() {}

  public static void mount(Javalin app) {
    app.before("/api/account/*", AuthFilter::authenticate);
    app.before("/api/transfers", AuthFilter::authenticate);
    app.before("/api/transfers/*", AuthFilter::authenticate);
    app.before("/api/deposits/*", AuthFilter::authenticate);
    app.before("/api/qr/*", AuthFilter::authenticate);
    app.before("/api/cards", AuthFilter::authenticate);
    app.before("/api/cards/*", AuthFilter::authenticate);
    app.before("/api/users/*", AuthFilter::authenticate);
    app.before("/api/transactions", AuthFilter::authenticate);
    app.before("/api/transactions/*", AuthFilter::authenticate);
    app.before("/api/operations/*", AuthFilter::authenticate);
    app.before("/api/auth/me", AuthFilter::authenticate);
  }

  private static void authenticate(Context ctx) {
    if (ctx.method() == HandlerType.OPTIONS) return;
    String header = ctx.header("Authorization");
    if (
      header == null || !header.startsWith("Bearer ")
    ) throw new AuthenticationRequiredException("Please log in first");
    try {
      var session = JwtUtil.verify(header.substring(7));
      var user = new AuthService().currentUser(session.userId());
      if (
        user.getSessionVersion() != session.version()
      ) throw new AuthenticationRequiredException(
        "Your PIN changed. Please log in again"
      );
      ctx.attribute("userId", session.userId());
      ctx.attribute("currentUser", user);
    } catch (JWTVerificationException e) {
      throw new AuthenticationRequiredException(
        "Session expired or invalid. Please log in again"
      );
    }
  }

  public static long currentUserId(Context ctx) {
    Long id = ctx.attribute("userId");
    if (id == null) throw new AuthenticationRequiredException(
      "Please log in first"
    );
    return id;
  }
}
