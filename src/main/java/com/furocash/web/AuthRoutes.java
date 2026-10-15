package com.furocash.web;

import com.furocash.dto.LoginRequest;
import com.furocash.dto.LoginResponse;
import com.furocash.dto.RegisterRequest;
import com.furocash.dto.UserResponse;
import com.furocash.model.User;
import com.furocash.service.AuthService;
import com.furocash.service.OtpService;
import com.furocash.util.JwtUtil;
import io.javalin.Javalin;

import java.util.Map;

public final class AuthRoutes {

    private AuthRoutes() {
    }

    public static void mount(Javalin app, AuthService authService, OtpService otpService) {

        app.post("/api/auth/register/request-code", ctx -> {
            RegisterRequest req = ctx.bodyAsClass(RegisterRequest.class);
            otpService.requestRegistrationCode(req.mobile(), req.pin(), req.fullName());
            ctx.json(Map.of("message", "Code sent"));
        });

        app.post("/api/auth/register", ctx -> {
            RegisterRequest req = ctx.bodyAsClass(RegisterRequest.class);
            otpService.verify(req.mobile(), req.otp());
            long id = authService.register(req.mobile(), req.pin(), req.fullName());
            ctx.status(201).json(Map.of("id", id));
        });

        app.post("/api/auth/login", ctx -> {
            LoginRequest req = ctx.bodyAsClass(LoginRequest.class);
            User user = authService.login(req.mobile(), req.pin());
            String token = JwtUtil.createToken(user.getId());
            ctx.json(new LoginResponse(token, UserResponse.from(user)));
        });
    }
}