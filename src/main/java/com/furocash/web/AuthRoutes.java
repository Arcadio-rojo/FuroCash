package com.furocash.web;

import com.furocash.dto.*;
import com.furocash.model.User;
import com.furocash.service.*;
import com.furocash.util.JwtUtil;
import io.javalin.Javalin;
import java.util.Map;

public final class AuthRoutes {

  private AuthRoutes() {}

  private static LoginResponse session(User user) {
    return new LoginResponse(
      JwtUtil.createToken(user.getId(), user.getSessionVersion()),
      UserResponse.from(user)
    );
  }

  public static void mount(
    Javalin app,
    AuthService authService,
    OtpService otpService
  ) {
    app.post("/api/auth/register/request-code", ctx -> {
      RegisterRequest req = ctx.bodyAsClass(RegisterRequest.class);
      otpService.requestRegistrationCode(
        req.mobile(),
        req.pin(),
        req.fullName()
      );
      ctx.json(Map.of("message", "Code sent"));
    });
    app.post("/api/auth/register", ctx -> {
      RegisterRequest req = ctx.bodyAsClass(RegisterRequest.class);
      ctx
        .status(201)
        .json(
          session(
            otpService.register(
              req.mobile(),
              req.pin(),
              req.fullName(),
              req.otp()
            )
          )
        );
    });
    app.post("/api/auth/login", ctx -> {
      LoginRequest req = ctx.bodyAsClass(LoginRequest.class);
      ctx.json(session(authService.login(req.mobile(), req.pin())));
    });
    app.get("/api/auth/me", ctx -> {
      User user = ctx.attribute("currentUser");
      ctx.json(UserResponse.from(user));
    });
    app.post("/api/auth/forgot-pin", ctx -> {
      ForgotPinRequest req = ctx.bodyAsClass(ForgotPinRequest.class);
      otpService.requestResetCode(req.mobile());
      ctx.json(
        Map.of(
          "message",
          "If this number is registered, a recovery code has been sent"
        )
      );
    });
    app.post("/api/auth/reset-pin", ctx -> {
      ResetPinRequest req = ctx.bodyAsClass(ResetPinRequest.class);
      otpService.resetPin(req.mobile(), req.otp(), req.pin());
      ctx.json(Map.of("message", "PIN updated. Please log in again"));
    });
  }
}
