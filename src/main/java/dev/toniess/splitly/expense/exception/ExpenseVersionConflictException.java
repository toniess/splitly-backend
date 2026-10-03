package dev.toniess.splitly.expense.exception;

import dev.toniess.splitly.common.exception.ConflictException;

public class ExpenseVersionConflictException extends ConflictException {
    public ExpenseVersionConflictException(String message) {
        super(message);
    }
}
