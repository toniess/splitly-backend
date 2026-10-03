package dev.toniess.splitly.expense.dto;

import java.util.List;

public record ExpenseFeedPage(
        List<ExpenseSummary> items,
        String nextCursor
) { }
