package dev.toniess.splitly.expense.split;

import java.math.BigDecimal;
import java.util.UUID;

public record SplitInput(UUID userId, BigDecimal value) {}
