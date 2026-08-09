package org.shalash.adminapi.exception;

public class UserNotAutherizedException extends RuntimeException {
    public UserNotAutherizedException(String message) {
        super(message);
    }
}
