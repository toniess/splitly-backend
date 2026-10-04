package dev.toniess.splitly.settlement.dto;

import dev.toniess.splitly.common.Money;
import dev.toniess.splitly.settlement.entity.Settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        UUID groupId,
        UUID fromUserId,
        UUID toUserId,
        BigDecimal amount,
        Instant settledAt
) {
    public static SettlementResponse fromSettlement(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getGroup().getId(),
                settlement.getFromUser().getId(),
                settlement.getToUser().getId(),
                Money.normalize(settlement.getAmount()),
                settlement.getSettledAt()
        );
    }
}
