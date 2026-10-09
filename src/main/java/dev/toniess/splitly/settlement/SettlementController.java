package dev.toniess.splitly.settlement;

import dev.toniess.splitly.common.idempotency.IdempotencyService;
import dev.toniess.splitly.settlement.dto.BalancesResponse;
import dev.toniess.splitly.settlement.dto.CreateSettlementRequest;
import dev.toniess.splitly.settlement.dto.SettlementResponse;
import dev.toniess.splitly.settlement.dto.TransferPlanResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class SettlementController {
    private final SettlementService settlementService;
    private final IdempotencyService idempotencyService;

    public SettlementController(SettlementService settlementService, IdempotencyService idempotencyService) {
        this.settlementService = settlementService;
        this.idempotencyService = idempotencyService;
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
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 256) String idempotencyKey,
            @RequestBody @Valid CreateSettlementRequest request
    ) {
        return idempotencyService.execute(
                actorId,
                idempotencyKey,
                "POST /groups/" + groupId + "/settlements",
                request,
                HttpStatus.CREATED.value(),
                SettlementResponse.class,
                () -> settlementService.createSettlement(
                        actorId, groupId, request.fromUserId(), request.toUserId(), request.amount()
                )
        );
    }
}
