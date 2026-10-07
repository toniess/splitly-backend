package dev.toniess.splitly.common.exception;

public abstract class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
