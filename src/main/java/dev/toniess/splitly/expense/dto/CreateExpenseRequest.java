package dev.toniess.splitly.expense.dto;

import dev.toniess.splitly.expense.entity.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateExpenseRequest (
        @NotBlank
        @Size(max = 100)
        String description,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 15, fraction = 2)
        BigDecimal amount,

        @NotNull
        UUID payerId,

        @NotNull
        @PastOrPresent
        Instant spentAt,

        @NotNull
        SplitType splitType,

        @NotEmpty @Valid List<ParticipantInput> participants
) implements ExpenseInput {
}