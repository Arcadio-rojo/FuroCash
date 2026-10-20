package com.furocash.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.furocash.dto.*;
import com.furocash.exception.InvalidInputException;
import com.furocash.service.DemoWalletService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.math.BigDecimal;

public final class DemoRoutes {

  private DemoRoutes() {}

  public record DepositRequest(BigDecimal amount, String method) {}

  public record RecipientRequest(
    String accountIdentifier,
    String receiverMobile
  ) {}

  public record QrRequest(String purpose, BigDecimal amount) {}

  public record QrResolveRequest(JsonNode payload) {}

  public static void mount(Javalin app, DemoWalletService wallet) {
    app.post("/api/deposits/demo", ctx -> {
      var req = body(ctx, DepositRequest.class);
      ctx
        .status(201)
        .json(
          wallet.prepareDeposit(
            AuthFilter.currentUserId(ctx),
            req.amount(),
            req.method(),
            ctx.header("Idempotency-Key")
          )
        );
    });
    app.post("/api/deposits/demo/{reference}/complete", ctx ->
      ctx.json(
        wallet.completeDeposit(
          AuthFilter.currentUserId(ctx),
          ctx.pathParam("reference"),
          ctx.header("Idempotency-Key")
        )
      )
    );
    app.post("/api/transfers/validate-recipient", ctx -> {
      var req = body(ctx, RecipientRequest.class);
      ctx.json(
        wallet.validateRecipient(
          AuthFilter.currentUserId(ctx),
          req.accountIdentifier(),
          req.receiverMobile()
        )
      );
    });
    app.post("/api/qr/requests", ctx -> {
      var req = body(ctx, QrRequest.class);
      ctx
        .status(201)
        .json(
          wallet.createQr(
            AuthFilter.currentUserId(ctx),
            req.purpose(),
            req.amount()
          )
        );
    });
    app.post("/api/qr/resolve", ctx -> {
      var req = body(ctx, QrResolveRequest.class);
      ctx.json(wallet.resolveQr(qrPayload(req.payload())));
    });
  }

  private static QrPayload qrPayload(JsonNode payload) {
    // Validate raw JSON types before conversion: Jackson otherwise coerces 1.5
    // or "1" into an integer version and accepts a schema we do not support.
    if (
      payload == null ||
      !payload.isObject() ||
      payload.size() != 6 ||
      !payload.path("app").isTextual() ||
      !payload.path("version").isIntegralNumber() ||
      !payload.path("version").canConvertToInt() ||
      !payload.path("purpose").isTextual() ||
      !payload.path("accountIdentifier").isTextual() ||
      !payload.path("reference").isTextual() ||
      !payload.has("amount") ||
      !(payload.get("amount").isNull() || payload.get("amount").isTextual())
    ) throw new InvalidInputException("Unsupported FuroCash QR schema");
    return new QrPayload(
      payload.get("app").textValue(),
      payload.get("version").intValue(),
      payload.get("purpose").textValue(),
      payload.get("accountIdentifier").textValue(),
      payload.get("reference").textValue(),
      payload.get("amount").isNull() ? null : payload.get("amount").textValue()
    );
  }

  private static <T> T body(Context ctx, Class<T> type) {
    T value = ctx.bodyAsClass(type);
    if (value == null) throw new InvalidInputException(
      "Request details are required"
    );
    return value;
  }
}
