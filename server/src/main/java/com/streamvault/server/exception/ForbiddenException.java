package com.streamvault.server.exception;

/** Returned as 403 Forbidden with its message shown to the user (e.g. "Registration is closed"). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
