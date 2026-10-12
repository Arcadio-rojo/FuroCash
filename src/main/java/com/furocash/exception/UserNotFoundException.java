package com.furocash.exception;

public class UserNotFoundException extends FuroCashException {
    public UserNotFoundException(String message) {
        super(message);
    }
}