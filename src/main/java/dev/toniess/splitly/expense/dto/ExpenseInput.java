package dev.toniess.splitly.expense.dto;

import dev.toniess.splitly.expense.entity.SplitType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ExpenseInput {
    BigDecimal amount();
    SplitType splitType();
    UUID payerId();
    List<ParticipantInput> participants();
}