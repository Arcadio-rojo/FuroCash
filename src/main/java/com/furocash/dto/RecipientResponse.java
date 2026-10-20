package com.furocash.dto;

public record RecipientResponse(
  String accountIdentifier,
  String fullName,
  String mobileNumber
) {}
