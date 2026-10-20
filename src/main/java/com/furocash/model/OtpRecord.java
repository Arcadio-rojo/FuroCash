package com.furocash.model;

import java.time.LocalDateTime;

public record OtpRecord(
  String mobileNumber,
  String codeHash,
  LocalDateTime expiresAt,
  int attempts,
  LocalDateTime lastSentAt,
  boolean consumed
) {}
