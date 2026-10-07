package dev.toniess.splitly.settlement;

import dev.toniess.splitly.settlement.dto.BalancesResponse;
import dev.toniess.splitly.settlement.dto.CreateSettlementRequest;
import dev.toniess.splitly.settlement.dto.SettlementResponse;
import dev.toniess.splitly.settlement.dto.TransferPlanResponse;
import dev.toniess.splitly.settlement.entity.Settlement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class SettlementController {
    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/balances")
    @ResponseStatus(HttpStatus.OK)
    public BalancesResponse getBalances(
            @AuthenticationPrincipal UUID actorId,
            @PathVariable UUID groupId
    ) {
        return settlementService.getBalances(actorId, groupId);
    }


    @GetMapping("/settlement-plan")
    @ResponseStatus(HttpStatus.OK)
    public TransferPlanResponse getTransferPlan(
            @AuthenticationPrincipal UUID actorId,
            @PathVariable UUID groupId
    ) {
        return settlementService.plan(actorId, groupId);
    }

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse createSettlement(
            @AuthenticationPrincipal UUID actorId,
            @PathVariable UUID groupId,
            @RequestBody @Valid CreateSettlementRequest request
    ) {
        return settlementService.createSettlement(actorId, groupId, request.fromUserId(), request.toUserId(), request.amount());
    }
}
