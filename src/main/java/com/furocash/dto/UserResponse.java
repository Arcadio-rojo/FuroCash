package com.furocash.dto;

import com.furocash.model.User;
import java.math.BigDecimal;

public record UserResponse(long id, String mobileNumber, String fullName, BigDecimal balance) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getMobileNumber(), user.getFullName(), user.getBalance());
    }
}