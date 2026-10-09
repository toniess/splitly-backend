package dev.toniess.splitly.settlement;

import dev.toniess.splitly.IntegrationTest;
import dev.toniess.splitly.expense.ExpenseRepository;
import dev.toniess.splitly.expense.ExpenseService;
import dev.toniess.splitly.expense.dto.CreateExpenseRequest;
import dev.toniess.splitly.expense.dto.ParticipantInput;
import dev.toniess.splitly.expense.entity.SplitType;
import dev.toniess.splitly.group.GroupMemberRepository;
import dev.toniess.splitly.group.GroupRepository;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.group.exception.GroupNotFoundException;
import dev.toniess.splitly.settlement.dto.BalanceSummary;
import dev.toniess.splitly.settlement.dto.BalancesResponse;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Проверка расчёта сальдо на настоящей базе.
 *
 * <p>Каждый тест идёт в своей транзакции и откатывается — база остаётся нетронутой.
 */

@Transactional
class BalanceIntegrationTest extends IntegrationTest {

    @Autowired SettlementService settlementService;
    @Autowired ExpenseService expenseService;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired EntityManager entityManager;

    private User anton;
    private User maria;
    private User igor;
    private User outsider;
    private Group group;

    @BeforeEach
    void setUp() {
        anton = userRepository.save(new User(unique("anton"), "stub", "Антон"));
        maria = userRepository.save(new User(unique("maria"), "stub", "Мария"));
        igor = userRepository.save(new User(unique("igor"), "stub", "Игорь"));
        outsider = userRepository.save(new User(unique("outsider"), "stub", "Посторонний"));

        group = groupRepository.save(new Group("Поездка", "UZS", anton, unique("C").substring(0, 8)));
        groupMemberRepository.save(new GroupMember(group, GroupRole.OWNER, anton));
        groupMemberRepository.save(new GroupMember(group, GroupRole.MEMBER, maria));
        groupMemberRepository.save(new GroupMember(group, GroupRole.MEMBER, igor));
        groupMemberRepository.flush();
    }

    @Test
    @DisplayName("Плательщику должны, остальным — он")
    void payerIsOwedWhatOthersConsumed() {
        createExpense(anton, "300.00", anton, maria, igor);

        assertThat(balanceOf(anton)).isEqualByComparingTo("200.00");
        assertThat(balanceOf(maria)).isEqualByComparingTo("-100.00");
        assertThat(balanceOf(igor)).isEqualByComparingTo("-100.00");
    }

    @Test
    @DisplayName("Участник, не попавший ни в одну трату, виден с нулём")
    void memberWithoutActivityHasZeroBalance() {
        createExpense(anton, "100.00", anton, maria);

        BalancesResponse balances = settlementService.getBalances(anton.getId(), group.getId());

        assertThat(balances.items()).hasSize(3);
        assertThat(balanceOf(igor)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Встречные траты гасят друг друга")
    void oppositeExpensesCancelOut() {
        createExpense(anton, "100.00", anton, maria);
        createExpense(maria, "100.00", anton, maria);

        assertThat(balanceOf(anton)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(maria)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Удалённая трата в расчёте не участвует")
    void deletedExpenseIsExcluded() {
        UUID deleted = createExpense(anton, "100.00", anton, maria);
        expenseService.delete(anton.getId(), deleted);
        expenseRepository.flush();

        assertThat(balanceOf(anton)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(maria)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Перевод обнуляет сальдо обоих участников")
    void settlementClearsDebt() {
        createExpense(anton, "100.00", anton, maria);
        assertThat(balanceOf(maria)).isEqualByComparingTo("-50.00");

        insertSettlement(maria, anton, new BigDecimal("50.00"));

        assertThat(balanceOf(anton)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(maria)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Траты другой группы на сальдо не влияют")
    void otherGroupDoesNotLeakIn() {
        Group other = groupRepository.save(new Group("Другая", "UZS", anton, unique("D").substring(0, 8)));
        groupMemberRepository.save(new GroupMember(other, GroupRole.OWNER, anton));
        groupMemberRepository.save(new GroupMember(other, GroupRole.MEMBER, maria));
        groupMemberRepository.flush();

        createExpense(anton, "100.00", anton, maria);
        expenseService.create(anton.getId(), other.getId(),
                new CreateExpenseRequest("Чужая трата", new BigDecimal("999.00"), anton.getId(),
                        Instant.parse("2026-10-02T10:00:00Z"), SplitType.EQUAL,
                        List.of(new ParticipantInput(anton.getId(), null),
                                new ParticipantInput(maria.getId(), null))));
        expenseRepository.flush();

        assertThat(balanceOf(anton)).isEqualByComparingTo("50.00");
        assertThat(balanceOf(maria)).isEqualByComparingTo("-50.00");
    }

    @Test
    @DisplayName("Посторонний не видит балансы группы")
    void hidesBalancesFromNonMember() {
        assertThatThrownBy(() -> settlementService.getBalances(outsider.getId(), group.getId()))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    @DisplayName("Суммы приходят с двумя знаками после запятой")
    void amountsAreNormalizedToTwoDecimals() {
        createExpense(anton, "100.00", anton, maria, igor);

        BalancesResponse balances = settlementService.getBalances(anton.getId(), group.getId());

        assertThat(balances.items()).allSatisfy(item ->
                assertThat(item.balance().scale()).isEqualTo(2));
    }

    @Test
    @DisplayName("Обязательный кейс ТЗ: сумма сальдо равна нулю на 50 случайных тратах")
    void sumOfBalancesIsZeroAfterFiftyRandomExpenses() {
        Random random = new Random(20261004);
        List<User> members = List.of(anton, maria, igor);

        for (int i = 0; i < 50; i++) {
            User payer = members.get(random.nextInt(members.size()));

            // случайное непустое подмножество участников
            List<User> participants = new ArrayList<>();
            while (participants.isEmpty()) {
                for (User candidate : members) {
                    if (random.nextBoolean()) {
                        participants.add(candidate);
                    }
                }
            }

            // суммы с «неудобным» остатком: 0.01 … 999.99
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(99_999), 2);
            createExpense(payer, amount.toPlainString(), participants.toArray(new User[0]));
        }
        expenseRepository.flush();

        BalancesResponse balances = settlementService.getBalances(anton.getId(), group.getId());

        BigDecimal sum = balances.items().stream()
                .map(BalanceSummary::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum)
                .as("сальдо: %s", balances.items())
                .isEqualByComparingTo("0.00");
    }

    // ---------- вспомогательное ----------

    private UUID createExpense(User payer, String amount, User... participants) {
        List<ParticipantInput> inputs = new ArrayList<>();
        for (User participant : participants) {
            inputs.add(new ParticipantInput(participant.getId(), null));
        }
        return expenseService.create(anton.getId(), group.getId(),
                new CreateExpenseRequest("Трата", new BigDecimal(amount), payer.getId(),
                        Instant.parse("2026-10-02T10:00:00Z"), SplitType.EQUAL, inputs)).id();
    }

    /**
     * Переводы пока вставляются напрямую: у {@code Settlement} нет публичного конструктора,
     * а эндпоинта POST /settlements ещё не существует. Заменить на вызов сервиса,
     * когда он появится.
     */
    private void insertSettlement(User from, User to, BigDecimal amount) {
        entityManager.createNativeQuery("""
                        INSERT INTO settlements (group_id, from_user_id, to_user_id, amount)
                        VALUES (:groupId, :fromId, :toId, :amount)
                        """)
                .setParameter("groupId", group.getId())
                .setParameter("fromId", from.getId())
                .setParameter("toId", to.getId())
                .setParameter("amount", amount)
                .executeUpdate();
    }

    private BigDecimal balanceOf(User user) {
        expenseRepository.flush();
        return settlementService.getBalances(anton.getId(), group.getId()).items().stream()
                .filter(item -> item.userId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("участник " + user.getDisplayName() + " не найден в балансах"))
                .balance();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
