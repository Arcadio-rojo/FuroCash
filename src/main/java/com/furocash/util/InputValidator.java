package com.furocash.util;

import java.math.BigDecimal;

public final class InputValidator {
    private InputValidator(){
    }

    public static boolean isValidMobile(String mobile){
        if (mobile == null){
            return false;
        }
        return mobile.matches("^09\\d{9}$");
    }

    public static boolean isValidPin(String pin){
        if (pin == null){
            return false;
        }
        return pin.matches("^(\\d{4})$");
    }

    public static boolean isValidAmount(BigDecimal amount){
        if (amount == null){
            return false;
        }
        return amount.compareTo(BigDecimal.ZERO) > 0
                && amount.stripTrailingZeros().scale() <= 2;    }

    public static boolean isValidFullName(String fullName){
        if(fullName == null || fullName.isBlank()){
            return false;
        }
        return fullName.length() <=100;
    }
}
