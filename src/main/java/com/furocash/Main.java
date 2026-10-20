package com.furocash;

import com.furocash.service.*;
import com.furocash.sms.*;
import com.furocash.util.*;
import com.furocash.web.*;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.nio.file.*;
import java.util.Map;

public class Main {

  public static Javalin createApp(SmsSender sender) {
    Javalin app = Javalin.create(config -> {
      config.http.maxRequestSize = 16384;
      config.bundledPlugins.enableCors(cors ->
        cors.addRule(rule ->
          rule.allowHost(
            AppConfig.get("FRONTEND_ORIGIN", "http://localhost:5173")
          )
        )
      );
      if (Files.isDirectory(Path.of("frontend/dist"))) config.staticFiles.add(
        "frontend/dist",
        Location.EXTERNAL
      );
    });
    ApiExceptionHandler.mount(app);
    AuthFilter.mount(app);
    AuthRoutes.mount(app, new AuthService(), new OtpService(sender));
    AccountRoutes.mount(app, new AccountService(), new TransferService());
    DemoRoutes.mount(app, new DemoWalletService());
    CardRoutes.mount(app, new CardService());
    ProfileRoutes.mount(app, new ProfileService());
    app.get("/api/health", ctx ->
      ctx.json(
        Map.of(
          "status",
          "UP",
          "demoCashIn",
          AppConfig.enabled("DEMO_CASH_IN"),
          "smsMode",
          AppConfig.get("SMS_MODE", "philsms")
        )
      )
    );
    app.after(ctx -> {
      ctx.header("X-Content-Type-Options", "nosniff");
      if (ctx.path().startsWith("/api/")) ctx.header(
        "Cache-Control",
        "no-store"
      );
    });
    return app;
  }

  public static void main(String[] args) {
    SmsSender smsSender = SmsSenders.fromEnvironment();
    JwtUtil.validateConfiguration();
    DBConnection.migrate();
    int port = Integer.parseInt(AppConfig.get("PORT", "7070"));
    Javalin app = createApp(smsSender).start(port);
    Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    System.out.println("FuroCash is ready at http://localhost:" + port);
    if (
      "console".equalsIgnoreCase(AppConfig.get("SMS_MODE", "philsms"))
    ) System.out.println(
      "Console SMS enabled: verification codes appear in this terminal."
    );
  }
}
