package com.furocash.exception;

public class AccountLockedException extends FuroCashException {
    public AccountLockedException(String message) {
        super(message);
    }
}