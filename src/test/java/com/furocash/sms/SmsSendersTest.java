package com.furocash.sms;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

class SmsSendersTest {

  String previousMode, previousKey, previousPhilToken, previousPhilSender;

  @BeforeEach
  void remember() {
    previousMode = System.getProperty("SMS_MODE");
    previousKey = System.getProperty("SEMAPHORE_API_KEY");
    previousPhilToken = System.getProperty("PHILSMS_API_TOKEN");
    previousPhilSender = System.getProperty("PHILSMS_SENDER_ID");
  }

  @AfterEach
  void restore() {
    restore("SMS_MODE", previousMode);
    restore("SEMAPHORE_API_KEY", previousKey);
    restore("PHILSMS_API_TOKEN", previousPhilToken);
    restore("PHILSMS_SENDER_ID", previousPhilSender);
  }

  private static void restore(String key, String value) {
    if (value == null) System.clearProperty(key);
    else System.setProperty(key, value);
  }

  @Test
  void configuredRealSmsUsesSemaphore() {
    System.setProperty("SMS_MODE", "semaphore");
    System.setProperty("SEMAPHORE_API_KEY", "test-key-only");
    assertInstanceOf(SemaphoreSmsSender.class, SmsSenders.fromEnvironment());
  }

  @Test
  void missingRealSmsCredentialsNeverFallBackToConsole() {
    System.setProperty("SMS_MODE", "semaphore");
    System.setProperty("SEMAPHORE_API_KEY", "");
    assertThrows(IllegalStateException.class, SmsSenders::fromEnvironment);
  }

  @Test
  void misspelledSmsModeNeverFallsBackToConsole() {
    System.setProperty("SMS_MODE", "semphore");
    assertThrows(IllegalStateException.class, SmsSenders::fromEnvironment);
  }

  @Test
  void consoleIsAvailableOnlyWhenExplicitlySelected() {
    System.setProperty("SMS_MODE", "console");
    assertInstanceOf(ConsoleSmsSender.class, SmsSenders.fromEnvironment());
  }

  @Test
  void configuredPhilSmsUsesPhilSmsSender() {
    System.setProperty("SMS_MODE", "philsms");
    System.setProperty("PHILSMS_API_TOKEN", "test-token-only");
    System.setProperty("PHILSMS_SENDER_ID", "PhilSMS");
    assertInstanceOf(PhilSmsSender.class, SmsSenders.fromEnvironment());
  }

  @Test
  void missingPhilSmsTokenCannotFallBackToAnotherProvider() {
    System.setProperty("SMS_MODE", "philsms");
    System.setProperty("PHILSMS_API_TOKEN", "");
    assertThrows(IllegalStateException.class, SmsSenders::fromEnvironment);
  }
}
