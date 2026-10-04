package dev.toniess.splitly.group;


import dev.toniess.splitly.common.InviteCodeGenerator;
import dev.toniess.splitly.expense.exception.NotAGroupMemberException;
import dev.toniess.splitly.group.dto.GroupMemberResponse;
import dev.toniess.splitly.group.dto.GroupResponse;
import dev.toniess.splitly.group.dto.GroupSummary;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.group.exception.AlreadyMemberException;
import dev.toniess.splitly.group.exception.GroupNotFoundException;
import dev.toniess.splitly.settlement.exception.BalanceNotFoundException;
import dev.toniess.splitly.settlement.exception.OutstandingBalanceException;
import dev.toniess.splitly.settlement.SettlementService;
import dev.toniess.splitly.settlement.dto.BalanceSummary;
import dev.toniess.splitly.settlement.dto.BalancesResponse;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.entity.User;
import dev.toniess.splitly.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class GroupService {
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final SettlementService settlementService;

    public GroupService(UserRepository userRepository, GroupRepository groupRepository, GroupMemberRepository groupMemberRepository, InviteCodeGenerator inviteCodeGenerator, SettlementService settlementService) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.settlementService = settlementService;
    }

    @Transactional
    public GroupResponse createGroup(UUID actorId, String name, String currency) {
        final User user = userRepository.findById(actorId).orElseThrow(() -> new UserNotFoundException(actorId));

        final Group group = new Group(name, currency, user, inviteCodeGenerator.generate());
        groupRepository.saveAndFlush(group);

        final GroupMember groupMember = new GroupMember(group, GroupRole.OWNER, user);
        groupMemberRepository.saveAndFlush(groupMember);

        return GroupResponse.from(group);
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> findGroupsOfUser(UUID actorId) {
        return groupRepository.findAllByUserId(actorId)
                .stream()
                .map(GroupSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(UUID actorId, UUID groupId) {
        requireGroupMemberOrNotFound(actorId, groupId);
        final Group group = groupRepository.findById(groupId).orElseThrow(() -> GroupNotFoundException.byId(groupId));

        return GroupResponse.from(group);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(UUID actorId, UUID groupId) {
        requireGroupMemberOrNotFound(actorId, groupId);

        return groupMemberRepository.findAllByGroupId(groupId)
                .stream()
                .map(GroupMemberResponse::from)
                .toList();
    }

    @Transactional
    public GroupMemberResponse joinByInviteCode(UUID actorId, String inviteCode) {
        final Group group = groupRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> GroupNotFoundException.byInviteCode(inviteCode));
        if (groupMemberRepository.existsGroupMemberByUserIdAndGroupId(actorId, group.getId())) {
            throw new AlreadyMemberException(actorId, group.getId());
        }

        final User user = userRepository.findById(actorId).orElseThrow(() -> new UserNotFoundException(actorId));
        final GroupMember newMember = new GroupMember(group, GroupRole.MEMBER, user);
        groupMemberRepository.saveAndFlush(newMember);
        return GroupMemberResponse.from(newMember);
    }

    @Transactional
    public void deleteMember(UUID actorId, UUID groupId) {
        requireGroupMemberOrNotFound(actorId, groupId);

        final BalancesResponse balances = settlementService.getBalances(actorId, groupId);
        final BalanceSummary actorsBalance = balances.items().stream()
                .filter(b -> b.userId().equals(actorId))
                .findFirst().orElseThrow(() -> new BalanceNotFoundException(actorId, groupId));
        if (actorsBalance.balance().compareTo(BigDecimal.ZERO) != 0) {
            throw OutstandingBalanceException.ofAmount(actorsBalance.balance());
        }

        final GroupMember groupMember = groupMemberRepository.findByUserIdAndGroupId(actorId, groupId)
                .orElseThrow(() -> NotAGroupMemberException.user(actorId, groupId));
        if (groupMember.getRole() == GroupRole.OWNER) {
            final Optional<GroupMember> otherMember = groupMemberRepository.findAllByGroupId(groupId)
                    .stream().filter(gm -> !gm.getUser().getId().equals(actorId))
                    .min(Comparator.comparing(gm -> gm.getUser().getId()));

            otherMember.ifPresent(member -> member.setRole(GroupRole.OWNER));
        }

        groupMemberRepository.delete(groupMember);
    }

    private void requireGroupMemberOrNotFound(UUID userId, UUID groupId) {
        if (!groupMemberRepository.existsGroupMemberByUserIdAndGroupId(userId, groupId)) {
            throw GroupNotFoundException.byId(groupId);
        }
    }

}
