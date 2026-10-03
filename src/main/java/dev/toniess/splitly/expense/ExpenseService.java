package dev.toniess.splitly.expense;

import dev.toniess.splitly.expense.dto.*;
import dev.toniess.splitly.expense.entity.Expense;
import dev.toniess.splitly.expense.entity.ExpenseShare;
import dev.toniess.splitly.expense.exception.ExpenseForbiddenException;
import dev.toniess.splitly.expense.exception.ExpenseNotFoundException;
import dev.toniess.splitly.expense.exception.ExpenseVersionConflictException;
import dev.toniess.splitly.expense.split.ShareAmount;
import dev.toniess.splitly.expense.split.SplitInput;
import dev.toniess.splitly.expense.split.SplitStrategyRegistry;
import dev.toniess.splitly.group.GroupMemberRepository;
import dev.toniess.splitly.group.GroupRepository;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.common.exception.UnprocessableException;
import dev.toniess.splitly.expense.exception.NotAGroupMemberException;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.group.exception.GroupNotFoundException;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final SplitStrategyRegistry splitStrategyRegistry;
    private final GroupRepository groupRepository;

    public ExpenseService(ExpenseRepository expenseRepository, GroupMemberRepository groupMemberRepository, UserRepository userRepository, SplitStrategyRegistry splitStrategyRegistry, GroupRepository groupRepository) {
        this.expenseRepository = expenseRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.splitStrategyRegistry = splitStrategyRegistry;
        this.groupRepository = groupRepository;
    }

    @Transactional
    public ExpenseResponse create(UUID actorId, UUID groupId, CreateExpenseRequest request) {
        final Map<UUID, User> userMap = validateParticipantsAndGetUserMap(actorId, groupId, request);
        final List<ShareAmount> split = buildShares(request);
        final Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> GroupNotFoundException.byId(groupId));

        final Expense expense = new Expense(
                request.description(),
                request.amount(),
                userMap.get(request.payerId()),
                group,
                group.getCurrency(),
                request.spentAt(),
                request.splitType(),
                split.stream().map(p -> new ExpenseShare(
                        userMap.get(p.userId()),
                        p.amount()
                )).toList()
        );
        expenseRepository.saveAndFlush(expense);
        return ExpenseResponse.fromExpense(expense);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getById(UUID actorId, UUID expenseId) {
        final Expense expense = expenseRepository.findByIdAndDeletedAtIsNull(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        requireMemberOrNotFound(actorId, expense.getGroup().getId(), expenseId);

        return ExpenseResponse.fromExpense(expense);
    }

    @Transactional(readOnly = true)
    public ExpenseFeedPage getFeed(UUID actorId, UUID groupId, String cursor, int limit) {
        if (!groupMemberRepository.existsGroupMemberByUserIdAndGroupId(actorId, groupId)) {
            throw GroupNotFoundException.byId(groupId);
        }

        List<Expense> expenseList;
        if (cursor == null) {
            expenseList = expenseRepository.findFirstPage(groupId, limit + 1);
        } else {
            final Cursor cursorEntity = Cursor.decode(cursor);
            expenseList = expenseRepository.findPage(groupId, limit + 1, cursorEntity.spentAt(), cursorEntity.id());
        }

        final String nextCursor;
        if (expenseList.size() > limit) {
            expenseList = expenseList.subList(0, limit);
            nextCursor = new Cursor(expenseList.getLast().getSpentAt(), expenseList.getLast().getId()).encode();
        } else {
            nextCursor = null;
        }

        return new ExpenseFeedPage(expenseList.stream().map(ExpenseSummary::fromExpense).toList(), nextCursor);
    }

    @Transactional
    public ExpenseResponse update(UUID actorId, UUID expenseId, UpdateExpenseRequest request) {
        final Expense expense = expenseRepository.findByIdAndDeletedAtIsNull(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        requireMemberOrNotFound(actorId, expense.getGroup().getId(), expenseId);
        requireCanModify(actorId, expense);
        if (expense.getVersion() != request.version()) {
            throw new ExpenseVersionConflictException(
                    "Expense version has already been updated, version " + request.version() + " is old one");
        }

        final Map<UUID, User> userMap = validateParticipantsAndGetUserMap(actorId, expense.getGroup().getId(), request);
        final List<ShareAmount> split = buildShares(request);
        final List<ExpenseShare> newShares = split.stream()
                .map(p -> new ExpenseShare(userMap.get(p.userId()), p.amount()))
                .toList();

        expense.update(
                request.description(),
                request.amount(),
                userMap.get(request.payerId()),
                request.spentAt(),
                request.splitType(),
                newShares
        );

        expenseRepository.flush();
        return ExpenseResponse.fromExpense(expense);
    }

    @Transactional
    public void delete(UUID actorId, UUID expenseId) {
        final Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        requireMemberOrNotFound(actorId, expense.getGroup().getId(), expenseId);
        requireCanModify(actorId, expense);

        if (expense.getDeletedAt() == null) {
            expense.setDeletedAt(Instant.now());
        }
    }


    private void requireMemberOrNotFound(UUID actorId, UUID groupId, UUID expenseId) {
        if (!groupMemberRepository.existsGroupMemberByUserIdAndGroupId(actorId, groupId)) {
            throw new ExpenseNotFoundException(expenseId);
        }
    }

    private void requireCanModify(UUID actorId, Expense expense) {
        if (!expense.getPayer().getId().equals(actorId) &&
                !groupMemberRepository.existsByUserIdAndGroupIdAndRole(
                        actorId, expense.getGroup().getId(), GroupRole.OWNER))
        {
            throw new ExpenseForbiddenException(expense.getId());
        }
    }

    private Map<UUID, User> validateParticipantsAndGetUserMap(UUID actorId, UUID groupId, ExpenseInput expenseInput) {
        final Set<UUID> groupMembersIds = groupMemberRepository.findUserIdsByGroupId(groupId);

        if (!groupMembersIds.contains(actorId)) {
            throw GroupNotFoundException.byId(groupId);
        }

        if (!groupMembersIds.contains(expenseInput.payerId())) {
            throw NotAGroupMemberException.payer(expenseInput.payerId(), groupId);
        }

        final Set<UUID> allParticipants = expenseInput.participants().stream()
                .map(ParticipantInput::userId).collect(Collectors.toSet());

        if (allParticipants.isEmpty()) {
            throw new UnprocessableException("Expense must have at least one participant");
        }

        final Set<UUID> outsiders = new HashSet<>(allParticipants);
        outsiders.removeAll(groupMembersIds);
        if (!outsiders.isEmpty()) {
            throw NotAGroupMemberException.participants(outsiders, groupId);
        }

        final Set<UUID> involvedUserIds = new HashSet<>(allParticipants);
        involvedUserIds.add(expenseInput.payerId());

        final Map<UUID, User> userMap = userRepository.findAllById(involvedUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        if (userMap.size() != involvedUserIds.size()) {
            final Set<UUID> missing = new HashSet<>(involvedUserIds);
            missing.removeAll(userMap.keySet());
            throw new IllegalStateException("Users referenced by expense do not exist: " + missing);
        }

        return userMap;
    }

    private List<ShareAmount> buildShares(ExpenseInput expenseInput) {
        final List<SplitInput> splitInputList = expenseInput.participants().stream()
                .map(p -> new SplitInput(p.userId(), p.value())).toList();
        return splitStrategyRegistry.resolve(expenseInput.splitType())
                .split(expenseInput.amount(), splitInputList);
    }
}
