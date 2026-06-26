package com.jvmd.authservice.dto;

import org.apache.hc.core5.http.HttpStatus;

public record UserRegisterResponse(HttpStatus httpStatus, String message) {
    }