package com.furocash.dto;

import java.math.BigDecimal;

public record CardPurchaseRequest(BigDecimal amount, String merchant) {}
