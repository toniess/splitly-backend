package dev.toniess.splitly.settlement.dto;

import java.util.List;

public record TransferPlanResponse(
        List<Transfer> items
) {
}
