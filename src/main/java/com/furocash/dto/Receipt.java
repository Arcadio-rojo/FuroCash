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
  String createdAt,
  BigDecimal fee,
  BigDecimal total,
  String method,
  String senderAccount,
  String senderName,
  String recipientAccount,
  String recipientName
) {
  public Receipt(
    String reference,
    String kind,
    String status,
    BigDecimal amount,
    BigDecimal balance,
    String accountIdentifier,
    String counterparty,
    String note,
    String createdAt
  ) {
    this(
      reference,
      kind,
      status,
      amount,
      balance,
      accountIdentifier,
      counterparty,
      note,
      createdAt,
      null,
      null,
      null,
      null,
      null,
      null,
      null
    );
  }
}
