package com.furocash.web;

import com.furocash.dto.ErrorResponse;
import com.furocash.exception.*;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import org.slf4j.LoggerFactory;

public final class ApiExceptionHandler {

  private ApiExceptionHandler() {}

  public static void mount(Javalin app) {
    app.exception(Exception.class, (e, ctx) -> {
      LoggerFactory.getLogger(ApiExceptionHandler.class).error(
        "Unhandled API error",
        e
      );
      ctx
        .status(500)
        .json(
          new ErrorResponse("Something went wrong. Please try again later")
        );
    });
    app.exception(HttpResponseException.class, (e, ctx) ->
      ctx
        .status(e.getStatus())
        .json(
          new ErrorResponse(
            e.getStatus() == 400
              ? "Invalid request. Check your input"
              : e.getMessage()
          )
        )
    );
    app.exception(FuroCashException.class, (e, ctx) -> {
      int status = statusFor(e);
      if (status == 500) LoggerFactory.getLogger(
        ApiExceptionHandler.class
      ).error("API operation failed", e);
      ctx
        .status(status)
        .json(
          new ErrorResponse(
            status == 500
              ? "Something went wrong. Please try again later"
              : e.getMessage()
          )
        );
    });
  }

  private static int statusFor(FuroCashException e) {
    if (
      e instanceof InvalidInputException ||
      e instanceof InvalidAmountException ||
      e instanceof InvalidOtpException
    ) return 400;
    if (
      e instanceof InvalidCredentialsException ||
      e instanceof AuthenticationRequiredException
    ) return 401;
    if (e instanceof AccountLockedException) return 423;
    if (e instanceof UserNotFoundException) return 404;
    if (e instanceof UserAlreadyExistsException || e instanceof OperationConflictException) return 409;
    if (e instanceof InsufficientFundsException) return 422;
    if (e instanceof TooManyRequestsException) return 429;
    if (e instanceof SmsSendException) return 502;
    return 500;
  }
}
