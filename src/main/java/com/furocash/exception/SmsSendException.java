package com.furocash.exception;

public class SmsSendException extends FuroCashException{
    public SmsSendException(String message) { super(message); }

    public SmsSendException(String message, Throwable cause) {
        super(message, cause);

    }

}
