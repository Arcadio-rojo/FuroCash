package com.furocash.web;

import com.furocash.dto.ErrorResponse;
import com.furocash.exception.*;
import io.javalin.Javalin;

public final class ApiExceptionHandler {

    private ApiExceptionHandler() {
    }

    public static void mount(Javalin app) {
        app.exception(FuroCashException.class, (e, ctx) -> {
            int status = statusFor(e);
            if (status == 500) {
                e.printStackTrace();
            }
            ctx.status(status).json(new ErrorResponse(e.getMessage()));
        });
    }

    private static int statusFor(FuroCashException e) {
        if (e instanceof InvalidInputException || e instanceof InvalidAmountException) return 400;
        if (e instanceof InvalidCredentialsException || e instanceof AuthenticationRequiredException) return 401;
        if (e instanceof AccountLockedException) return 423;
        if (e instanceof UserNotFoundException) return 404;
        if (e instanceof UserAlreadyExistsException) return 409;
        if (e instanceof InsufficientFundsException) return 422;
        return 500;
    }
}