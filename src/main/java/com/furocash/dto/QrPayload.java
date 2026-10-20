package com.furocash.dto;

public record QrPayload(
  String app,
  int version,
  String purpose,
  String accountIdentifier,
  String reference,
  String amount
) {}
