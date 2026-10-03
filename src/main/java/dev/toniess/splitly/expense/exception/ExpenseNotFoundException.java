package dev.toniess.splitly.expense.exception;


import dev.toniess.splitly.common.exception.NotFoundException;

import java.util.UUID;

public class ExpenseNotFoundException extends NotFoundException {
    public ExpenseNotFoundException(UUID expenseId) {
        super("Expense with id " + expenseId + " was not found");
    }
    public ExpenseNotFoundException() {
        super("Expenses were not found");
    }
}
