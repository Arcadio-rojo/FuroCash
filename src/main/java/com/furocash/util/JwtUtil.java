package com.furocash.util;

import com.auth0.jwt.*;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class JwtUtil {

  private static class Signing {

    private static final Algorithm ALGORITHM = Algorithm.HMAC256(readSecret());
    private static final JWTVerifier VERIFIER = JWT.require(ALGORITHM)
      .withIssuer("furocash")
      .build();
  }

  private JwtUtil() {}

  public static void validateConfiguration() {
    readSecret();
  }

  private static String readSecret() {
    String secret = AppConfig.get("JWT_SECRET", "");
    if (
      secret.length() < 32 || secret.startsWith("change_me")
    ) throw new IllegalStateException(
      "Set JWT_SECRET to a random string of at least 32 characters in .env"
    );
    return secret;
  }

  public static String createToken(long userId, int version) {
    Instant now = Instant.now();
    return JWT.create()
      .withIssuer("furocash")
      .withSubject(String.valueOf(userId))
      .withClaim("ver", version)
      .withIssuedAt(now)
      .withExpiresAt(now.plus(60, ChronoUnit.MINUTES))
      .sign(Signing.ALGORITHM);
  }

  public record Session(long userId, int version) {}

  public static Session verify(String token) {
    var jwt = Signing.VERIFIER.verify(token);
    try {
      Integer version = jwt.getClaim("ver").asInt();
      long id = Long.parseLong(jwt.getSubject());
      if (
        version == null || id <= 0 || jwt.getExpiresAt() == null
      ) throw new IllegalArgumentException();
      return new Session(id, version);
    } catch (RuntimeException e) {
      throw new JWTVerificationException("Invalid session claims", e);
    }
  }
}
