package dev.toniess.splitly.expense.exception;

import dev.toniess.splitly.common.exception.ForbiddenException;

import java.util.UUID;


public class ExpenseForbiddenException extends ForbiddenException {
    public ExpenseForbiddenException(UUID expenseId) {
        super("You are not allowed to delete expense with id " + expenseId +
                ". You are not the owner of the group or the payer of the expense.");
    }
}
