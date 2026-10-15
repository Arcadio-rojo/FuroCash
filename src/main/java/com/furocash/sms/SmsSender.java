package com.furocash.sms;

public interface SmsSender {
    void send(String mobile, String message);
}
