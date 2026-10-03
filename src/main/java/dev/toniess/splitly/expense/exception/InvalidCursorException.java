package dev.toniess.splitly.expense.exception;

import dev.toniess.splitly.common.exception.BadRequestException;

public class InvalidCursorException extends BadRequestException {
    public InvalidCursorException(String message) {
        super(message);
    }
    public InvalidCursorException(String message, Throwable cause) {
        super(message, cause);
    }
}
