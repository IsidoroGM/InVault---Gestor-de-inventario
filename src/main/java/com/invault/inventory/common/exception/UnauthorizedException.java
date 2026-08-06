package com.invault.inventory.common.exception;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

/*
 * UnauthorizedException represents authentication failures that must be
 * returned as HTTP 401 without revealing which credential was incorrect.
 */
