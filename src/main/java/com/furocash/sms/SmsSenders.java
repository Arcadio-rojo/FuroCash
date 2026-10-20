package com.furocash.sms;

import com.furocash.util.AppConfig;
import java.util.Locale;

public final class SmsSenders {

  private SmsSenders() {}

  public static SmsSender fromEnvironment() {
    String mode = AppConfig.get("SMS_MODE", "philsms")
      .trim()
      .toLowerCase(Locale.ROOT);
    return switch (mode) {
      case "philsms" -> {
        String token = AppConfig.get("PHILSMS_API_TOKEN", "").trim();
        if (token.isEmpty()) throw new IllegalStateException(
          "Real SMS requires PHILSMS_API_TOKEN in the project-root .env"
        );
        yield new PhilSmsSender(
          token,
          AppConfig.get("PHILSMS_SENDER_ID", "PhilSMS").trim(),
          AppConfig.get(
            "PHILSMS_API_BASE_URL",
            "https://dashboard.philsms.com/api/v3/"
          ).trim()
        );
      }
      case "semaphore" -> {
        String key = AppConfig.get("SEMAPHORE_API_KEY", "").trim();
        if (key.isEmpty()) throw new IllegalStateException(
          "Real SMS requires SEMAPHORE_API_KEY in the project-root .env. Configure your Semaphore account and SMS credits before starting the backend."
        );
        yield new SemaphoreSmsSender(
          key,
          AppConfig.get("SEMAPHORE_SENDER_NAME", "").trim()
        );
      }
      case "console" -> new ConsoleSmsSender(); // Explicit development/testing only.
      default -> throw new IllegalStateException(
        "Unsupported SMS_MODE. Use philsms or semaphore for real SMS, or explicitly select console for development testing."
      );
    };
  }
}
