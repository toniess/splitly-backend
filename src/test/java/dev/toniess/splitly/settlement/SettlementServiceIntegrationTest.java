package dev.toniess.splitly.settlement;

import dev.toniess.splitly.expense.ExpenseRepository;
import dev.toniess.splitly.expense.ExpenseService;
import dev.toniess.splitly.expense.dto.CreateExpenseRequest;
import dev.toniess.splitly.expense.dto.ParticipantInput;
import dev.toniess.splitly.expense.entity.SplitType;
import dev.toniess.splitly.expense.exception.NotAGroupMemberException;
import dev.toniess.splitly.group.GroupMemberRepository;
import dev.toniess.splitly.group.GroupRepository;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.group.exception.GroupNotFoundException;
import dev.toniess.splitly.settlement.dto.SettlementResponse;
import dev.toniess.splitly.settlement.dto.Transfer;
import dev.toniess.splitly.settlement.exception.CreateSettlementForbiddenException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Фиксация переводов и план взаиморасчётов на настоящей базе.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SettlementServiceIntegrationTest {

    @Autowired SettlementService settlementService;
    @Autowired ExpenseService expenseService;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;

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

    // ---------- фиксация перевода ----------

    @Test
    @DisplayName("Отправитель фиксирует перевод и получает его в ответе")
    void senderRecordsSettlement() {
        SettlementResponse response = settlementService.createSettlement(
                maria.getId(), group.getId(), maria.getId(), anton.getId(), new BigDecimal("50.00"));

        assertThat(response.fromUserId()).isEqualTo(maria.getId());
        assertThat(response.toUserId()).isEqualTo(anton.getId());
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.settledAt()).as("момент перевода проставляется базой").isNotNull();
        assertThat(response.groupId()).isEqualTo(group.getId());
    }

    @Test
    @DisplayName("Перевод меняет сальдо обоих участников")
    void settlementChangesBothBalances() {
        createExpense(anton, "100.00", anton, maria);
        assertThat(balanceOf(maria)).isEqualByComparingTo("-50.00");

        settlementService.createSettlement(
                maria.getId(), group.getId(), maria.getId(), anton.getId(), new BigDecimal("50.00"));

        assertThat(balanceOf(maria)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(anton)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Частичный перевод уменьшает долг, но не закрывает его")
    void partialSettlementReducesDebt() {
        createExpense(anton, "100.00", anton, maria);

        settlementService.createSettlement(
                maria.getId(), group.getId(), maria.getId(), anton.getId(), new BigDecimal("20.00"));

        assertThat(balanceOf(maria)).isEqualByComparingTo("-30.00");
        assertThat(balanceOf(anton)).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("Фиксировать перевод может только его отправитель")
    void onlySenderMayRecordSettlement() {
        assertThatThrownBy(() -> settlementService.createSettlement(
                anton.getId(), group.getId(), maria.getId(), anton.getId(), new BigDecimal("50.00")))
                .as("получатель не вправе")
                .isInstanceOf(CreateSettlementForbiddenException.class);

        assertThatThrownBy(() -> settlementService.createSettlement(
                igor.getId(), group.getId(), maria.getId(), anton.getId(), new BigDecimal("50.00")))
                .as("посторонний участник группы не вправе")
                .isInstanceOf(CreateSettlementForbiddenException.class);
    }

    @Test
    @DisplayName("Отправитель не из группы отклоняется")
    void rejectsSenderOutsideGroup() {
        assertThatThrownBy(() -> settlementService.createSettlement(
                anton.getId(), group.getId(), outsider.getId(), anton.getId(), new BigDecimal("50.00")))
                .isInstanceOf(NotAGroupMemberException.class);
    }

    @Test
    @DisplayName("Получатель не из группы отклоняется")
    void rejectsRecipientOutsideGroup() {
        assertThatThrownBy(() -> settlementService.createSettlement(
                anton.getId(), group.getId(), anton.getId(), outsider.getId(), new BigDecimal("50.00")))
                .isInstanceOf(NotAGroupMemberException.class);
    }

    @Test
    @DisplayName("Посторонний не может фиксировать переводы в чужой группе")
    void rejectsSettlementFromNonMember() {
        assertThatThrownBy(() -> settlementService.createSettlement(
                outsider.getId(), group.getId(), anton.getId(), maria.getId(), new BigDecimal("50.00")))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // ---------- план взаиморасчётов ----------

    @Test
    @DisplayName("План закрывает долги минимальным числом переводов")
    void planSettlesDebtsWithFewestTransfers() {
        createExpense(anton, "300.00", anton, maria, igor);

        List<Transfer> plan = settlementService.plan(anton.getId(), group.getId()).items();

        assertThat(plan).hasSize(2);
        assertThat(plan).allSatisfy(transfer -> {
            assertThat(transfer.to()).isEqualTo(anton.getId());
            assertThat(transfer.amount()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    @DisplayName("Приёмка ТЗ: после применения всего плана все сальдо нулевые")
    void applyingWholePlanZeroesAllBalances() {
        createExpense(anton, "300.00", anton, maria, igor);
        createExpense(maria, "90.00", maria, igor);

        List<Transfer> plan = settlementService.plan(anton.getId(), group.getId()).items();
        assertThat(plan).isNotEmpty();

        // каждый перевод фиксирует его отправитель — как в реальном сценарии
        plan.forEach(transfer -> settlementService.createSettlement(
                transfer.from(), group.getId(), transfer.from(), transfer.to(), transfer.amount()));
        settlementRepository.flush();

        assertThat(settlementService.getBalances(anton.getId(), group.getId()).items())
                .allSatisfy(balance -> assertThat(balance.balance()).isEqualByComparingTo("0.00"));

        assertThat(settlementService.plan(anton.getId(), group.getId()).items())
                .as("после расчёта план пустой")
                .isEmpty();
    }

    @Test
    @DisplayName("Когда все рассчитались, план пустой")
    void planIsEmptyWhenNobodyOwes() {
        assertThat(settlementService.plan(anton.getId(), group.getId()).items()).isEmpty();
    }

    @Test
    @DisplayName("Посторонний не видит план группы")
    void hidesPlanFromNonMember() {
        assertThatThrownBy(() -> settlementService.plan(outsider.getId(), group.getId()))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // ---------- вспомогательное ----------

    private void createExpense(User payer, String amount, User... participants) {
        List<ParticipantInput> inputs = new ArrayList<>();
        for (User participant : participants) {
            inputs.add(new ParticipantInput(participant.getId(), null));
        }
        expenseService.create(anton.getId(), group.getId(),
                new CreateExpenseRequest("Трата", new BigDecimal(amount), payer.getId(),
                        Instant.parse("2026-10-02T10:00:00Z"), SplitType.EQUAL, inputs));
        expenseRepository.flush();
    }

    private BigDecimal balanceOf(User user) {
        expenseRepository.flush();
        return settlementService.getBalances(anton.getId(), group.getId()).items().stream()
                .filter(item -> item.userId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("нет сальдо для " + user.getDisplayName()))
                .balance();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
