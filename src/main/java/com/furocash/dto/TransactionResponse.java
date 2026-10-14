package com.furocash.dto;

import com.furocash.model.Transaction;
import java.math.BigDecimal;

public record TransactionResponse(long id, String type, BigDecimal amount, String details,
                                  String referenceNo, BigDecimal balanceAfter, String createdAt) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.getId(), t.getType().name(), t.getAmount(), t.getDetails(),
                t.getReferenceNo(), t.getBalanceAfter(), String.valueOf(t.getCreatedAt()));
    }
}