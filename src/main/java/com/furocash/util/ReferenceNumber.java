package com.furocash.util;

import java.util.UUID;

public final class ReferenceNumber {
    private ReferenceNumber() {
    }

    public static String generate() {
        return "FC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}