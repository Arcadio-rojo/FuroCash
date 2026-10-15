package com.furocash.sms;

public class ConsoleSmsSender implements SmsSender {
    @Override
    public void send(String mobile, String message){
        System.out.println("[SMS to " + mobile + "]" + message);
    }
}
