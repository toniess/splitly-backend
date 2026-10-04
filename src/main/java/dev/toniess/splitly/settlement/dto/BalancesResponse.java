package dev.toniess.splitly.settlement.dto;

import java.util.List;

public record BalancesResponse(
        List<BalanceSummary> items
) {
}
