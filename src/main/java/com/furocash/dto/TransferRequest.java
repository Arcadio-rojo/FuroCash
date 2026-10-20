package com.furocash.dto;

import java.math.BigDecimal;

public record TransferRequest(
  String receiverMobile,
  BigDecimal amount,
  String receiverAccount,
  String note,
  String qrReference,
  String reviewReference
) {
  public TransferRequest(
    String receiverMobile,
    BigDecimal amount,
    String receiverAccount,
    String note,
    String qrReference
  ) {
    this(receiverMobile, amount, receiverAccount, note, qrReference, null);
  }
}
