package com.furocash.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileUpdateRequest(String fullName, String email) {}
