package com.furocash.dto;

import com.furocash.model.User;
import java.math.BigDecimal;
import com.furocash.util.BankingSupport;

public record UserResponse(long id, String mobileNumber, String fullName, BigDecimal balance,
                           String accountIdentifier, String email, String createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getMobileNumber(), user.getFullName(), user.getBalance(),
                BankingSupport.accountIdentifier(user.getId()), user.getEmail(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
    }
}
