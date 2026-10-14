package com.furocash.web;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.furocash.exception.AuthenticationRequiredException;
import com.furocash.util.JwtUtil;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;

public final class AuthFilter {

    private AuthFilter() {
    }

    public static void mount(Javalin app) {
        app.before("/api/account/*", AuthFilter::authenticate);
        app.before("/api/transfers", AuthFilter::authenticate);
        app.before("/api/transactions", AuthFilter::authenticate);
    }

    private static void authenticate(Context ctx) {
        if (ctx.method() == HandlerType.OPTIONS) {
            return;   // browser preflight requests carry no token
        }
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AuthenticationRequiredException("Please log in first");
        }
        try {
            long userId = JwtUtil.verify(header.substring(7));
            ctx.attribute("userId", userId);
        } catch (JWTVerificationException e) {
            throw new AuthenticationRequiredException("Session expired or invalid. Please log in again");
        }
    }

    public static long currentUserId(Context ctx) {
        Long id = ctx.attribute("userId");
        if (id == null) {
            throw new AuthenticationRequiredException("Please log in first");
        }
        return id;
    }
}