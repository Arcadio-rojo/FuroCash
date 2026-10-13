package com.furocash.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class JwtUtil {

    private static final long EXPIRY_MINUTES = 60;
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(readSecret());
    private static final JWTVerifier VERIFIER = JWT.require(ALGORITHM).build();

    private JwtUtil() {
    }

    private static String readSecret() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be set to at least 32 characters");
        }
        return secret;
    }

    public static String createToken(long userId) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withIssuedAt(now)
                .withExpiresAt(now.plus(EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .sign(ALGORITHM);
    }

    public static long verify(String token) {
        DecodedJWT jwt = VERIFIER.verify(token);
        return Long.parseLong(jwt.getSubject());
    }
}