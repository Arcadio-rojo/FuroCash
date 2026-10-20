package com.furocash.web;

import com.furocash.dto.CardLimitRequest;
import com.furocash.dto.CardPurchaseRequest;
import com.furocash.service.CardService;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import java.util.UUID;

public final class CardRoutes {
  private CardRoutes() {}

  public static void mount(Javalin app, CardService cards) {
    app.post("/api/cards", ctx -> ctx.status(201).json(cards.create(AuthFilter.currentUserId(ctx))));
    app.get("/api/cards/my", ctx -> {
      var card = cards.mine(AuthFilter.currentUserId(ctx));
      if (card == null) ctx.contentType("application/json").result("null");
      else ctx.json(card);
    });
    app.patch("/api/cards/{id}/freeze", ctx -> ctx.json(cards.setFrozen(AuthFilter.currentUserId(ctx), cardId(ctx), true)));
    app.patch("/api/cards/{id}/unfreeze", ctx -> ctx.json(cards.setFrozen(AuthFilter.currentUserId(ctx), cardId(ctx), false)));
    app.patch("/api/cards/{id}/limit", ctx -> {
      long userId = AuthFilter.currentUserId(ctx);
      var request = ctx.bodyAsClass(CardLimitRequest.class);
      if (request == null) throw new BadRequestResponse("Daily limit is required");
      ctx.json(cards.setLimit(userId, cardId(ctx), request.dailyLimit()));
    });
    app.get("/api/cards/{id}/transactions", ctx -> ctx.json(cards.transactions(AuthFilter.currentUserId(ctx), cardId(ctx))));
    app.post("/api/cards/{id}/simulate-purchase", ctx -> ctx.json(cards.purchase(
      AuthFilter.currentUserId(ctx), cardId(ctx), ctx.bodyAsClass(CardPurchaseRequest.class), ctx.header("Idempotency-Key")
    )));
  }

  private static UUID cardId(Context ctx) {
    try {
      String raw = ctx.pathParam("id");
      UUID id = UUID.fromString(raw);
      if (!id.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
      return id;
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("A valid demo card ID is required");
    }
  }
}
