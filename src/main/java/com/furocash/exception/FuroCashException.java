package com.furocash.exception;

public class FuroCashException extends RuntimeException {

    public FuroCashException(String message) {
        super(message);
    }

    public FuroCashException(String message, Throwable cause) {
        super(message, cause);
    }
}