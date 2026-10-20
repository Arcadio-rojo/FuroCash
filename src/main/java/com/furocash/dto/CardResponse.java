package com.furocash.dto;

import java.math.BigDecimal;

public record CardResponse(String id, String identifier, String maskedIdentifier,
                           String cardholderName, String status, BigDecimal dailyLimit,
                           BigDecimal spentToday, String expiresOn, String createdAt) {}
