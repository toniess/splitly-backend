package dev.toniess.splitly.expense.dto;

import dev.toniess.splitly.common.Money;
import dev.toniess.splitly.expense.entity.Expense;
import dev.toniess.splitly.expense.entity.ExpenseShare;
import dev.toniess.splitly.expense.entity.SplitType;
import dev.toniess.splitly.expense.split.ShareAmount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID groupId,
        UUID payerId,
        BigDecimal amount,
        String currency,
        String description,
        Instant spentAt,
        SplitType splitType,
        Integer version,
        Instant createdAt,
        List<ShareResponse> shares
) {
    public record ShareResponse (
            UUID userId,
            String displayName,
            BigDecimal amount
    ) {}

    public static ExpenseResponse fromExpense(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getPayer().getId(),
                Money.normalize(expense.getAmount()),
                expense.getCurrency(),
                expense.getDescription(),
                expense.getSpentAt(),
                expense.getSplitType(),
                expense.getVersion(),
                expense.getCreatedAt(),
                expense.getShares().stream().sorted(Comparator.comparing(share -> share.getUser().getId()))
                        .map(share -> new ShareResponse(
                            share.getUser().getId(),
                            share.getUser().getDisplayName(),
                            Money.normalize(share.getAmount()
                        )
                    )
                ).toList()
        );
    }
}
