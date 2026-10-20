package com.furocash.dto;

import java.math.BigDecimal;

public record Receipt(
  String reference,
  String kind,
  String status,
  BigDecimal amount,
  BigDecimal balance,
  String accountIdentifier,
  String counterparty,
  String note,
  String createdAt
) {}
