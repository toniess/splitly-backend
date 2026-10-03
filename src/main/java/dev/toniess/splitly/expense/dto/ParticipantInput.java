package dev.toniess.splitly.expense.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record ParticipantInput(
        @NotNull
        UUID userId,

        @PositiveOrZero
        @Digits(integer = 15, fraction = 2)
        BigDecimal value
) {}
