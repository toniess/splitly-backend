package dev.toniess.splitly.expense;

import dev.toniess.splitly.expense.dto.CreateExpenseRequest;
import dev.toniess.splitly.expense.dto.ExpenseFeedPage;
import dev.toniess.splitly.expense.dto.ExpenseResponse;
import dev.toniess.splitly.expense.dto.ParticipantInput;
import dev.toniess.splitly.expense.dto.UpdateExpenseRequest;
import dev.toniess.splitly.expense.entity.SplitType;
import dev.toniess.splitly.expense.exception.ExpenseForbiddenException;
import dev.toniess.splitly.expense.exception.ExpenseNotFoundException;
import dev.toniess.splitly.expense.exception.ExpenseVersionConflictException;
import dev.toniess.splitly.expense.exception.NotAGroupMemberException;
import dev.toniess.splitly.expense.exception.SharesDoNotMatchAmountException;
import dev.toniess.splitly.group.GroupMemberRepository;
import dev.toniess.splitly.group.GroupRepository;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.group.exception.GroupNotFoundException;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты идут на настоящем PostgreSQL — поднятом через docker compose.
 *
 * <p>{@code @Transactional} на классе означает, что каждый тест выполняется в своей транзакции,
 * которая по окончании откатывается. База остаётся в том же состоянии, в каком была,
 * поэтому тесты не мешают друг другу и не засоряют данные.
 *
 * <p>В итерации 6 локальную базу заменит Testcontainers, и зависимость от docker compose исчезнет.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExpenseServiceIntegrationTest {

    @Autowired ExpenseService expenseService;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;

    private User owner;
    private User member;
    private User outsider;
    private Group group;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User(unique("owner"), "stub", "Антон"));
        member = userRepository.save(new User(unique("member"), "stub", "Мария"));
        outsider = userRepository.save(new User(unique("outsider"), "stub", "Игорь"));

        group = groupRepository.save(new Group("Поездка", "UZS", owner, unique("CODE").substring(0, 8)));
        groupMemberRepository.save(new GroupMember(group, GroupRole.OWNER, owner));
        groupMemberRepository.save(new GroupMember(group, GroupRole.MEMBER, member));
        groupMemberRepository.flush();
    }

    // ---------- создание ----------

    @Test
    @DisplayName("Трата делится поровну, валюта берётся из группы")
    void createsExpenseSplitEqually() {
        ExpenseResponse created = expenseService.create(owner.getId(), group.getId(),
                request(new BigDecimal("100.00"), owner.getId(), SplitType.EQUAL,
                        participant(owner), participant(member)));

        assertThat(created.amount()).isEqualByComparingTo("100.00");
        assertThat(created.currency()).isEqualTo("UZS");
        assertThat(created.version()).isZero();
        assertThat(created.shares()).hasSize(2);
        assertThat(created.shares()).allSatisfy(share ->
                assertThat(share.amount()).isEqualByComparingTo("50.00"));
    }

    @Test
    @DisplayName("Остаток от деления достаётся первому участнику, сумма долей сходится")
    void distributesRemainderAndKeepsInvariant() {
        ExpenseResponse created = expenseService.create(owner.getId(), group.getId(),
                request(new BigDecimal("0.01"), owner.getId(), SplitType.EQUAL,
                        participant(owner), participant(member)));

        BigDecimal sum = created.shares().stream()
                .map(ExpenseResponse.ShareResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo("0.01");
        assertThat(created.shares()).extracting(ExpenseResponse.ShareResponse::amount)
                .anySatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.01"))
                .anySatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.00"));
    }

    @Test
    @DisplayName("Доли считает сервер: присланные клиентом значения для EQUAL игнорируются")
    void ignoresClientSuppliedAmountsForEqualSplit() {
        ExpenseResponse created = expenseService.create(owner.getId(), group.getId(),
                request(new BigDecimal("100.00"), owner.getId(), SplitType.EQUAL,
                        new ParticipantInput(owner.getId(), new BigDecimal("1.00")),
                        new ParticipantInput(member.getId(), new BigDecimal("99.00"))));

        assertThat(created.shares()).allSatisfy(share ->
                assertThat(share.amount()).isEqualByComparingTo("50.00"));
    }

    @Test
    @DisplayName("EXACT с суммой долей, не равной сумме траты, отклоняется")
    void rejectsExactSplitWithMismatchedSum() {
        assertThatThrownBy(() -> expenseService.create(owner.getId(), group.getId(),
                request(new BigDecimal("100.00"), owner.getId(), SplitType.EXACT,
                        new ParticipantInput(owner.getId(), new BigDecimal("50.00")),
                        new ParticipantInput(member.getId(), new BigDecimal("49.99")))))
                .isInstanceOf(SharesDoNotMatchAmountException.class);
    }

    @Test
    @DisplayName("Участник не из группы отклоняется")
    void rejectsParticipantOutsideGroup() {
        assertThatThrownBy(() -> expenseService.create(owner.getId(), group.getId(),
                request(new BigDecimal("100.00"), owner.getId(), SplitType.EQUAL,
                        participant(owner), participant(outsider))))
                .isInstanceOf(NotAGroupMemberException.class);
    }

    @Test
    @DisplayName("Плательщик не из группы отклоняется")
    void rejectsPayerOutsideGroup() {
        assertThatThrownBy(() -> expenseService.create(owner.getId(), group.getId(),
                request(new BigDecimal("100.00"), outsider.getId(), SplitType.EQUAL,
                        participant(owner), participant(member))))
                .isInstanceOf(NotAGroupMemberException.class);
    }

    @Test
    @DisplayName("Посторонний не может создать трату в чужой группе")
    void rejectsExpenseFromNonMember() {
        assertThatThrownBy(() -> expenseService.create(outsider.getId(), group.getId(),
                request(new BigDecimal("100.00"), owner.getId(), SplitType.EQUAL,
                        participant(owner), participant(member))))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // ---------- чтение ----------

    @Test
    @DisplayName("Трату видит любой участник группы")
    void anyGroupMemberCanReadExpense() {
        UUID expenseId = createDefaultExpense().id();

        assertThat(expenseService.getById(member.getId(), expenseId).id()).isEqualTo(expenseId);
    }

    @Test
    @DisplayName("Чужая трата по прямому id даёт «не найдено», а не «запрещено»")
    void hidesExpenseFromNonMember() {
        UUID expenseId = createDefaultExpense().id();

        assertThatThrownBy(() -> expenseService.getById(outsider.getId(), expenseId))
                .isInstanceOf(ExpenseNotFoundException.class);
    }

    // ---------- изменение ----------

    @Test
    @DisplayName("Изменение суммы пересчитывает доли и увеличивает версию")
    void updateRecalculatesSharesAndBumpsVersion() {
        ExpenseResponse created = createDefaultExpense();

        ExpenseResponse updated = expenseService.update(owner.getId(), created.id(),
                updateRequest(new BigDecimal("200.00"), owner.getId(), created.version(),
                        participant(owner), participant(member)));

        assertThat(updated.amount()).isEqualByComparingTo("200.00");
        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThat(updated.shares()).allSatisfy(share ->
                assertThat(share.amount()).isEqualByComparingTo("100.00"));
    }

    @Test
    @DisplayName("Изменение с устаревшей версией отклоняется")
    void rejectsUpdateWithStaleVersion() {
        ExpenseResponse created = createDefaultExpense();
        expenseService.update(owner.getId(), created.id(),
                updateRequest(new BigDecimal("200.00"), owner.getId(), created.version(),
                        participant(owner), participant(member)));

        assertThatThrownBy(() -> expenseService.update(owner.getId(), created.id(),
                updateRequest(new BigDecimal("300.00"), owner.getId(), created.version(),
                        participant(owner), participant(member))))
                .isInstanceOf(ExpenseVersionConflictException.class);
    }

    @Test
    @DisplayName("Участник, не являющийся плательщиком или владельцем, изменить трату не может")
    void rejectsUpdateFromOrdinaryMember() {
        ExpenseResponse created = createDefaultExpense();

        assertThatThrownBy(() -> expenseService.update(member.getId(), created.id(),
                updateRequest(new BigDecimal("200.00"), owner.getId(), created.version(),
                        participant(owner), participant(member))))
                .isInstanceOf(ExpenseForbiddenException.class);
    }

    @Test
    @DisplayName("Владелец группы может изменить чужую трату")
    void allowsUpdateFromGroupOwner() {
        ExpenseResponse created = expenseService.create(member.getId(), group.getId(),
                request(new BigDecimal("100.00"), member.getId(), SplitType.EQUAL,
                        participant(owner), participant(member)));

        ExpenseResponse updated = expenseService.update(owner.getId(), created.id(),
                updateRequest(new BigDecimal("150.00"), member.getId(), created.version(),
                        participant(owner), participant(member)));

        assertThat(updated.amount()).isEqualByComparingTo("150.00");
    }

    // ---------- удаление ----------

    @Test
    @DisplayName("Удалённая трата перестаёт быть видимой")
    void softDeleteHidesExpense() {
        UUID expenseId = createDefaultExpense().id();

        expenseService.delete(owner.getId(), expenseId);

        assertThatThrownBy(() -> expenseService.getById(owner.getId(), expenseId))
                .isInstanceOf(ExpenseNotFoundException.class);
    }

    @Test
    @DisplayName("Строка траты остаётся в базе: история не удаляется физически")
    void softDeleteKeepsRow() {
        UUID expenseId = createDefaultExpense().id();

        expenseService.delete(owner.getId(), expenseId);
        expenseRepository.flush();

        assertThat(expenseRepository.findById(expenseId))
                .get()
                .satisfies(expense -> assertThat(expense.getDeletedAt()).isNotNull());
    }

    @Test
    @DisplayName("Повторное удаление не ломается и не меняет момент удаления")
    void deleteIsIdempotent() {
        UUID expenseId = createDefaultExpense().id();

        expenseService.delete(owner.getId(), expenseId);
        expenseRepository.flush();
        Instant firstDeletedAt = expenseRepository.findById(expenseId).orElseThrow().getDeletedAt();

        expenseService.delete(owner.getId(), expenseId);
        expenseRepository.flush();

        assertThat(expenseRepository.findById(expenseId).orElseThrow().getDeletedAt())
                .isEqualTo(firstDeletedAt);
    }

    @Test
    @DisplayName("Обычный участник не может удалить чужую трату")
    void rejectsDeleteFromOrdinaryMember() {
        UUID expenseId = createDefaultExpense().id();

        assertThatThrownBy(() -> expenseService.delete(member.getId(), expenseId))
                .isInstanceOf(ExpenseForbiddenException.class);
    }

    // ---------- лента ----------

    @Test
    @DisplayName("Лента листается курсором без дублей и пропусков")
    void feedPaginatesWithoutDuplicates() {
        List<UUID> created = List.of(
                createExpenseAt("Первая", Instant.parse("2026-10-01T10:00:00Z")),
                createExpenseAt("Вторая", Instant.parse("2026-10-02T10:00:00Z")),
                createExpenseAt("Третья", Instant.parse("2026-10-03T10:00:00Z")),
                createExpenseAt("Четвёртая", Instant.parse("2026-10-04T10:00:00Z")),
                createExpenseAt("Пятая", Instant.parse("2026-10-05T10:00:00Z")));
        expenseRepository.flush();

        ExpenseFeedPage first = expenseService.getFeed(owner.getId(), group.getId(), null, 2);
        ExpenseFeedPage second = expenseService.getFeed(owner.getId(), group.getId(), first.nextCursor(), 2);
        ExpenseFeedPage third = expenseService.getFeed(owner.getId(), group.getId(), second.nextCursor(), 2);

        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isNotNull();
        assertThat(second.items()).hasSize(2);
        assertThat(third.items()).hasSize(1);
        assertThat(third.nextCursor()).as("последняя страница не предлагает продолжения").isNull();

        List<UUID> seen = java.util.stream.Stream.of(first, second, third)
                .flatMap(page -> page.items().stream())
                .map(item -> item.id())
                .toList();

        assertThat(seen).containsExactlyInAnyOrderElementsOf(created);
    }

    @Test
    @DisplayName("Лента отсортирована от новых к старым")
    void feedIsSortedNewestFirst() {
        createExpenseAt("Старая", Instant.parse("2026-10-01T10:00:00Z"));
        createExpenseAt("Новая", Instant.parse("2026-10-05T10:00:00Z"));
        expenseRepository.flush();

        ExpenseFeedPage page = expenseService.getFeed(owner.getId(), group.getId(), null, 10);

        assertThat(page.items()).extracting(item -> item.description())
                .containsExactly("Новая", "Старая");
    }

    @Test
    @DisplayName("Удалённые траты в ленту не попадают")
    void feedSkipsDeletedExpenses() {
        UUID visible = createExpenseAt("Видимая", Instant.parse("2026-10-01T10:00:00Z"));
        UUID deleted = createExpenseAt("Удалённая", Instant.parse("2026-10-02T10:00:00Z"));
        expenseService.delete(owner.getId(), deleted);
        expenseRepository.flush();

        ExpenseFeedPage page = expenseService.getFeed(owner.getId(), group.getId(), null, 10);

        assertThat(page.items()).extracting(item -> item.id()).containsExactly(visible);
    }

    @Test
    @DisplayName("Посторонний не видит ленту группы")
    void hidesFeedFromNonMember() {
        assertThatThrownBy(() -> expenseService.getFeed(outsider.getId(), group.getId(), null, 10))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // ---------- вспомогательное ----------

    private ExpenseResponse createDefaultExpense() {
        return expenseService.create(owner.getId(), group.getId(),
                request(new BigDecimal("100.00"), owner.getId(), SplitType.EQUAL,
                        participant(owner), participant(member)));
    }

    private UUID createExpenseAt(String description, Instant spentAt) {
        return expenseService.create(owner.getId(), group.getId(),
                new CreateExpenseRequest(description, new BigDecimal("100.00"), owner.getId(),
                        spentAt, SplitType.EQUAL,
                        List.of(participant(owner), participant(member)))).id();
    }

    private static CreateExpenseRequest request(BigDecimal amount, UUID payerId, SplitType splitType,
                                                ParticipantInput... participants) {
        return new CreateExpenseRequest("Ужин", amount, payerId,
                Instant.parse("2026-10-02T10:00:00Z"), splitType, List.of(participants));
    }

    private static UpdateExpenseRequest updateRequest(BigDecimal amount, UUID payerId, Integer version,
                                                      ParticipantInput... participants) {
        return new UpdateExpenseRequest("Ужин (изменён)", amount, payerId,
                Instant.parse("2026-10-02T10:00:00Z"), SplitType.EQUAL, List.of(participants), version);
    }

    private static ParticipantInput participant(User user) {
        return new ParticipantInput(user.getId(), null);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
