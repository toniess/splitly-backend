package dev.toniess.splitly.settlement.dto;

import dev.toniess.splitly.common.Money;
import dev.toniess.splitly.settlement.BalanceProjection;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceSummary(
        UUID userId,
        String displayName,
        BigDecimal balance
) {
    public static BalanceSummary fromBalanceProjection(BalanceProjection balanceProjection) {
        return new BalanceSummary(
                balanceProjection.getUserId(),
                balanceProjection.getDisplayName(),
                Money.normalize(balanceProjection.getBalance())
        );
    }
}