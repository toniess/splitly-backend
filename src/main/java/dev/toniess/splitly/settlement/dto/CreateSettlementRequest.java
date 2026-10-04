package dev.toniess.splitly.settlement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSettlementRequest(
        @NotNull UUID fromUserId,
        @NotNull UUID toUserId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount
) {
    @AssertTrue(message = "Отправитель и получатель должны быть разными")
    public boolean isDifferentParticipants() {
        return fromUserId != null && !fromUserId.equals(toUserId);
    }
}
