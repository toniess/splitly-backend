package dev.toniess.splitly.group;

import dev.toniess.splitly.expense.ExpenseRepository;
import dev.toniess.splitly.expense.ExpenseService;
import dev.toniess.splitly.expense.dto.CreateExpenseRequest;
import dev.toniess.splitly.expense.dto.ParticipantInput;
import dev.toniess.splitly.expense.entity.SplitType;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.group.exception.GroupNotFoundException;
import dev.toniess.splitly.settlement.SettlementService;
import dev.toniess.splitly.settlement.exception.OutstandingBalanceException;
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
 * Выход из группы: проверка сальдо, передача владения, видимость после ухода.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeaveGroupIntegrationTest {

    @Autowired GroupService groupService;
    @Autowired SettlementService settlementService;
    @Autowired ExpenseService expenseService;
    @Autowired ExpenseRepository expenseRepository;
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

    @Test
    @DisplayName("Участник с нулевым сальдо выходит из группы")
    void memberWithZeroBalanceLeaves() {
        groupService.deleteMember(maria.getId(), group.getId());
        groupMemberRepository.flush();

        assertThat(groupMemberRepository.findUserIdsByGroupId(group.getId()))
                .containsExactlyInAnyOrder(anton.getId(), igor.getId());
    }

    @Test
    @DisplayName("Выход с долгом запрещён")
    void rejectsLeavingWithDebt() {
        createExpense(anton, "100.00", anton, maria);

        assertThatThrownBy(() -> groupService.deleteMember(maria.getId(), group.getId()))
                .isInstanceOf(OutstandingBalanceException.class)
                .hasMessageContaining("-50.00");
    }

    @Test
    @DisplayName("Выход с положительным сальдо тоже запрещён")
    void rejectsLeavingWhenOthersOweYou() {
        createExpense(anton, "100.00", anton, maria);

        assertThatThrownBy(() -> groupService.deleteMember(anton.getId(), group.getId()))
                .isInstanceOf(OutstandingBalanceException.class);
    }

    @Test
    @DisplayName("После расчёта выход разрешён")
    void allowsLeavingAfterDebtIsSettled() {
        createExpense(anton, "100.00", anton, maria);
        settlementService.createSettlement(
                maria.getId(), group.getId(), maria.getId(), anton.getId(), new BigDecimal("50.00"));

        groupService.deleteMember(maria.getId(), group.getId());
        groupMemberRepository.flush();

        assertThat(groupMemberRepository.findUserIdsByGroupId(group.getId()))
                .doesNotContain(maria.getId());
    }

    @Test
    @DisplayName("Посторонний не может выйти из чужой группы")
    void rejectsLeavingForNonMember() {
        assertThatThrownBy(() -> groupService.deleteMember(outsider.getId(), group.getId()))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    @DisplayName("Владение переходит другому участнику при выходе владельца")
    void transfersOwnershipWhenOwnerLeaves() {
        groupService.deleteMember(anton.getId(), group.getId());
        groupMemberRepository.flush();

        List<GroupMember> remaining = groupMemberRepository.findAllByGroupId(group.getId());

        assertThat(remaining).hasSize(2);
        assertThat(remaining).filteredOn(member -> member.getRole() == GroupRole.OWNER)
                .as("ровно один владелец после передачи")
                .hasSize(1);
    }

    @Test
    @DisplayName("Передача владения детерминирована: наименьший userId")
    void ownershipTransferIsDeterministic() {
        UUID expectedOwner = maria.getId().compareTo(igor.getId()) < 0 ? maria.getId() : igor.getId();

        groupService.deleteMember(anton.getId(), group.getId());
        groupMemberRepository.flush();

        assertThat(groupMemberRepository.findAllByGroupId(group.getId()))
                .filteredOn(member -> member.getRole() == GroupRole.OWNER)
                .extracting(member -> member.getUser().getId())
                .containsExactly(expectedOwner);
    }

    @Test
    @DisplayName("Последний участник может выйти, группа остаётся пустой")
    void lastMemberMayLeaveAndGroupBecomesEmpty() {
        groupService.deleteMember(maria.getId(), group.getId());
        groupService.deleteMember(igor.getId(), group.getId());
        groupService.deleteMember(anton.getId(), group.getId());
        groupMemberRepository.flush();

        assertThat(groupMemberRepository.findUserIdsByGroupId(group.getId())).isEmpty();
        assertThat(groupRepository.findById(group.getId()))
                .as("сама группа физически не удаляется")
                .isPresent();
    }

    @Test
    @DisplayName("Ушедший участник не виден в балансах, но его траты остаются")
    void departedMemberDisappearsFromBalances() {
        createExpense(anton, "100.00", anton, maria);
        settlementService.createSettlement(
                maria.getId(), group.getId(), maria.getId(), anton.getId(), new BigDecimal("50.00"));
        groupService.deleteMember(maria.getId(), group.getId());
        groupMemberRepository.flush();

        assertThat(settlementService.getBalances(anton.getId(), group.getId()).items())
                .extracting(balance -> balance.userId())
                .doesNotContain(maria.getId());

        assertThat(expenseService.getFeed(anton.getId(), group.getId(), null, 10).items())
                .as("история трат сохраняется")
                .hasSize(1);
    }

    @Test
    @DisplayName("Ушедший теряет доступ к группе")
    void departedMemberLosesAccess() {
        groupService.deleteMember(maria.getId(), group.getId());
        groupMemberRepository.flush();

        assertThatThrownBy(() -> settlementService.getBalances(maria.getId(), group.getId()))
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

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
