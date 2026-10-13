package com.furocash.dto;

public record LoginResponse(String token, UserResponse user) { }