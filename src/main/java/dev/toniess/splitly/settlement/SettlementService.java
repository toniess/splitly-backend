package dev.toniess.splitly.settlement;

import dev.toniess.splitly.expense.exception.NotAGroupMemberException;
import dev.toniess.splitly.group.GroupMemberRepository;
import dev.toniess.splitly.group.GroupRepository;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.group.exception.GroupNotFoundException;
import dev.toniess.splitly.settlement.dto.*;
import dev.toniess.splitly.settlement.entity.Settlement;
import dev.toniess.splitly.settlement.exception.CreateSettlementForbiddenException;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SettlementService {
    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TransferPlanner transferPlanner;

    public SettlementService(SettlementRepository settlementRepository, GroupRepository groupRepository, UserRepository userRepository, GroupMemberRepository groupMemberRepository, TransferPlanner transferPlanner) {
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.transferPlanner = transferPlanner;
    }

    @Transactional(readOnly = true)
    public BalancesResponse getBalances(UUID actorId, UUID groupId) {
        if (!groupMemberRepository.existsGroupMemberByUserIdAndGroupId(actorId, groupId)) {
            throw GroupNotFoundException.byId(groupId);
        }

        final List<BalanceProjection> balances = settlementRepository.getBalances(groupId);

        final BigDecimal sum = balances.stream().map(BalanceProjection::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("The sum of expenses doesn't match zero. Expected 0, got " + sum);
        }

        return new BalancesResponse(balances.stream().map(BalanceSummary::fromBalanceProjection).toList());
    }

    @Transactional(readOnly = true)
    public TransferPlanResponse plan(UUID actorId, UUID groupId) {
        final BalancesResponse balances = getBalances(actorId, groupId);

        final List<Transfer> transfersPlan = transferPlanner.plan(balances.items());
        return new TransferPlanResponse(transfersPlan);
    }

    public SettlementResponse createSettlement(UUID actorId, UUID groupId, UUID fromUserId, UUID toUserId, BigDecimal amount) {
        final Set<UUID> groupUserIds = groupMemberRepository.findUserIdsByGroupId(groupId);
        if (!groupUserIds.contains(actorId)) {
            throw GroupNotFoundException.byId(groupId);
        }

        if (!groupUserIds.contains(fromUserId)) {
            throw NotAGroupMemberException.user(fromUserId, groupId);
        }

        if (!groupUserIds.contains(toUserId)) {
            throw NotAGroupMemberException.user(toUserId, groupId);
        }

        if (!actorId.equals(fromUserId)) {
            throw new CreateSettlementForbiddenException("You have no rights to create this settlement");
        }

        final Settlement settlement = new Settlement(
            groupRepository.getReferenceById(groupId),
                userRepository.getReferenceById(fromUserId),
                userRepository.getReferenceById(toUserId),
                amount
        );
        settlementRepository.saveAndFlush(settlement);

        return SettlementResponse.fromSettlement(settlement);
    }

}
