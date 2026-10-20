package com.furocash.dto;

import com.furocash.model.Transaction;
import java.math.BigDecimal;

public record TransactionResponse(
  long id,
  String type,
  BigDecimal amount,
  String details,
  String referenceNo,
  BigDecimal balanceAfter,
  String createdAt,
  String status,
  String cardId,
  boolean receiptAvailable
) {
  public TransactionResponse(
    long id,
    String type,
    BigDecimal amount,
    String details,
    String referenceNo,
    BigDecimal balanceAfter,
    String createdAt,
    String status,
    String cardId
  ) {
    this(
      id,
      type,
      amount,
      details,
      referenceNo,
      balanceAfter,
      createdAt,
      status,
      cardId,
      false
    );
  }

  public static TransactionResponse from(Transaction t) {
    return new TransactionResponse(
      t.getId(),
      t.getType().name(),
      t.getAmount(),
      t.getDetails(),
      t.getReferenceNo(),
      t.getBalanceAfter(),
      String.valueOf(t.getCreatedAt()),
      t.getStatus(),
      t.getCardId(),
      t.isReceiptAvailable()
    );
  }
}
