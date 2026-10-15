package com.furocash.sms;

public final class SmsSenders {

    private SmsSenders() {
    }

    public static SmsSender fromEnvironment() {
        if ("semaphore".equalsIgnoreCase(System.getenv("SMS_MODE"))) {
            String key = System.getenv("SEMAPHORE_API_KEY");
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("SEMAPHORE_API_KEY is required when SMS_MODE=semaphore");
            }
            return new SemaphoreSmsSender(key, System.getenv("SEMAPHORE_SENDER_NAME"));
        }
        return new ConsoleSmsSender();
    }
}