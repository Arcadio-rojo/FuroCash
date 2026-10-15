package com.furocash;

import com.furocash.service.AuthService;
import com.furocash.service.OtpService;
import com.furocash.sms.SmsSenders;
import com.furocash.util.DBConnection;
import com.furocash.web.ApiExceptionHandler;
import com.furocash.web.AuthRoutes;
import com.furocash.web.AuthFilter;
import com.furocash.web.AccountRoutes;
import com.furocash.service.AccountService;
import com.furocash.service.TransferService;

import io.javalin.Javalin;

import java.util.Map;

public class Main {
    public static void main(String[] args) {
        DBConnection.migrate();

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors ->
                    cors.addRule(rule -> rule.allowHost("http://localhost:5173")));
        });

        ApiExceptionHandler.mount(app);
        AuthFilter.mount(app);
        AccountRoutes.mount(app, new AccountService(), new TransferService());
        AuthRoutes.mount(app, new AuthService(), new OtpService(SmsSenders.fromEnvironment()));
        app.get("/api/health", ctx -> ctx.json(Map.of("status", "UP")));

        app.start(7070);
        System.out.println("FuroCash API running on http://localhost:7070");
    }
}