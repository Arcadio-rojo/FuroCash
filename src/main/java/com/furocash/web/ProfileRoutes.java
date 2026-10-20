package com.furocash.web;

import com.furocash.dto.ProfileUpdateRequest;
import com.furocash.service.ProfileService;
import io.javalin.Javalin;

public final class ProfileRoutes {
  private ProfileRoutes() {}

  public static void mount(Javalin app, ProfileService profiles) {
    app.get("/api/users/me", ctx -> ctx.json(profiles.get(AuthFilter.currentUserId(ctx))));
    app.patch("/api/users/me", ctx -> ctx.json(profiles.update(
      AuthFilter.currentUserId(ctx), ctx.bodyAsClass(ProfileUpdateRequest.class)
    )));
  }
}
