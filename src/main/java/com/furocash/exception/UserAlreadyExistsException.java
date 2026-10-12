package com.furocash.exception;

public class UserAlreadyExistsException extends FuroCashException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}