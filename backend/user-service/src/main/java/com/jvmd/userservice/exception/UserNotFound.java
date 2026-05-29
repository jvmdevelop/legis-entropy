package com.jvmd.userservice.exception;

public class UserNotFound extends RuntimeException {
    public UserNotFound(String message) {
        super("User not found: " + message);
    }
}
