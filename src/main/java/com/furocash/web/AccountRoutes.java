package com.furocash.web;

import com.furocash.dto.CashInRequest;
import com.furocash.dto.TransactionResponse;
import com.furocash.dto.TransferRequest;
import com.furocash.service.AccountService;
import com.furocash.service.TransferService;
import com.furocash.service.DemoWalletService;
import io.javalin.Javalin;
import java.util.List;
import java.util.Map;

public final class AccountRoutes {

  private AccountRoutes() {}

  public static void mount(
    Javalin app,
    AccountService accountService,
    TransferService transferService
  ) {
    app.get("/api/account/balance", ctx -> {
      long userId = AuthFilter.currentUserId(ctx);
      ctx.json(Map.of("balance", accountService.getBalance(userId)));
    });

    app.post("/api/account/cash-in", ctx -> {
      if (
        !com.furocash.util.AppConfig.enabled("DEMO_CASH_IN")
      ) throw new io.javalin.http.ForbiddenResponse("Demo cash-in is disabled");
      long userId = AuthFilter.currentUserId(ctx);
      CashInRequest req = ctx.bodyAsClass(CashInRequest.class);
      ctx.json(new DemoWalletService().legacyCashIn(userId,req.amount(),ctx.header("Idempotency-Key")));
    });

    app.post("/api/transfers", ctx -> {
      long userId = AuthFilter.currentUserId(ctx);
      TransferRequest req = ctx.bodyAsClass(TransferRequest.class);
      ctx.json(new DemoWalletService().transfer(userId,req,ctx.header("Idempotency-Key")));
    });

    app.get("/api/transactions", ctx -> {
      long userId = AuthFilter.currentUserId(ctx);
      int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
      int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
      List<TransactionResponse> history = accountService
        .getHistory(userId, page, size)
        .stream()
        .map(TransactionResponse::from)
        .toList();
      ctx.json(history);
    });
  }
}
