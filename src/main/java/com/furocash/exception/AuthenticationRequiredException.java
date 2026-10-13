package com.furocash.exception;

public class AuthenticationRequiredException extends FuroCashException {
    public AuthenticationRequiredException(String message) {
        super(message);
    }
}