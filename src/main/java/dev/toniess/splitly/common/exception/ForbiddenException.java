package dev.toniess.splitly.common.exception;

abstract public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
