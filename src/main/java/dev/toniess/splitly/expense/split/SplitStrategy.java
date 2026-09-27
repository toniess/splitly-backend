package dev.toniess.splitly.expense.split;

import dev.toniess.splitly.expense.entity.SplitType;

import java.math.BigDecimal;
import java.util.List;

public interface SplitStrategy {
    SplitType type();
    List<ShareAmount> split(BigDecimal amount, List<SplitInput> participants);
}