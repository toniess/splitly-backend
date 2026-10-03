package dev.toniess.splitly.expense.dto;

import dev.toniess.splitly.common.Money;
import dev.toniess.splitly.expense.entity.Expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExpenseSummary(
        UUID id,
        String description,
        BigDecimal amount,
        UUID payerId,
        Instant spentAt
) {
    public static ExpenseSummary fromExpense(Expense expense) {
        return new ExpenseSummary(
                expense.getId(),
                expense.getDescription(),
                Money.normalize(expense.getAmount()),
                expense.getPayer().getId(),
                expense.getSpentAt()
        );
    }
}
