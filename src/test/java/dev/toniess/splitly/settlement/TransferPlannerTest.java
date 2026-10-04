package dev.toniess.splitly.settlement;

import dev.toniess.splitly.settlement.dto.BalanceSummary;
import dev.toniess.splitly.settlement.dto.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferPlannerTest {

    private final TransferPlanner planner = new TransferPlanner();

    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID U3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID U4 = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    @DisplayName("Один должник и два кредитора закрываются двумя переводами")
    void splitsSingleDebtBetweenTwoCreditors() {
        List<Transfer> plan = planner.plan(balances("-100.00", "60.00", "40.00"));

        assertThat(plan).hasSize(2);
        assertThat(plan).allSatisfy(transfer -> assertThat(transfer.from()).isEqualTo(U1));
        assertThat(plan).extracting(Transfer::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("60.00"), new BigDecimal("40.00"));
    }

    @Test
    @DisplayName("Двое рассчитываются одним переводом")
    void settlesTwoParticipantsWithSingleTransfer() {
        List<Transfer> plan = planner.plan(balances("-50.00", "50.00"));

        assertThat(plan).hasSize(1);
        assertThat(plan.getFirst().from()).isEqualTo(U1);
        assertThat(plan.getFirst().to()).isEqualTo(U2);
        assertThat(plan.getFirst().amount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Направление: платит тот, у кого сальдо отрицательное")
    void debtorPaysCreditor() {
        List<Transfer> plan = planner.plan(balances("70.00", "-70.00"));

        assertThat(plan.getFirst().from()).as("должник").isEqualTo(U2);
        assertThat(plan.getFirst().to()).as("кредитор").isEqualTo(U1);
    }

    @Test
    @DisplayName("Все рассчитались — план пустой")
    void returnsEmptyPlanWhenNobodyOwesAnything() {
        assertThat(planner.plan(balances("0.00", "0.00", "0.00"))).isEmpty();
    }

    @Test
    @DisplayName("Пустой список участников — пустой план")
    void returnsEmptyPlanForEmptyInput() {
        assertThat(planner.plan(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Участники с нулевым сальдо в план не попадают")
    void skipsParticipantsWithZeroBalance() {
        List<Transfer> plan = planner.plan(balances("-30.00", "0.00", "30.00"));

        assertThat(plan).hasSize(1);
        assertThat(plan.getFirst().from()).isEqualTo(U1);
        assertThat(plan.getFirst().to()).isEqualTo(U3);
    }

    @Test
    @DisplayName("Копеечный долг закрывается переводом на копейку")
    void handlesSingleCent() {
        List<Transfer> plan = planner.plan(balances("-0.01", "0.01"));

        assertThat(plan).hasSize(1);
        assertThat(plan.getFirst().amount()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("Четверо: три перевода вместо шести попарных")
    void settlesFourParticipantsWithThreeTransfers() {
        List<Transfer> plan = planner.plan(balances("-50.00", "-30.00", "70.00", "10.00"));

        assertThat(plan).hasSize(3);
        assertThat(applyPlan(balances("-50.00", "-30.00", "70.00", "10.00"), plan))
                .allSatisfy((userId, balance) -> assertThat(balance).isEqualByComparingTo("0.00"));
    }

    @Test
    @DisplayName("Одинаковые сальдо дают один и тот же план при любом порядке на входе")
    void isDeterministicRegardlessOfInputOrder() {
        List<BalanceSummary> straight = List.of(
                balance(U1, "-40.00"), balance(U2, "-40.00"), balance(U3, "80.00"));
        List<BalanceSummary> shuffled = List.of(
                balance(U3, "80.00"), balance(U2, "-40.00"), balance(U1, "-40.00"));

        assertThat(planner.plan(straight)).isEqualTo(planner.plan(shuffled));
    }

    @Test
    @DisplayName("Инвариант: после применения плана все сальдо равны нулю")
    void applyingPlanZeroesAllBalances() {
        List<BalanceSummary> balances = balances("-125.50", "-74.50", "180.00", "20.00");

        Map<UUID, BigDecimal> after = applyPlan(balances, planner.plan(balances));

        assertThat(after).allSatisfy((userId, balance) ->
                assertThat(balance).as("сальдо %s", userId).isEqualByComparingTo("0.00"));
    }

    @Test
    @DisplayName("Инвариант: план обнуляет сальдо и укладывается в N−1 перевод на случайных данных")
    void satisfiesInvariantsOnRandomBalances() {
        Random random = new Random(20261004);

        for (int iteration = 0; iteration < 1_000; iteration++) {
            int participantCount = 2 + random.nextInt(8);
            List<BalanceSummary> balances = randomZeroSumBalances(participantCount, random);

            List<Transfer> plan = planner.plan(balances);
            long nonZero = balances.stream().filter(b -> b.balance().signum() != 0).count();

            assertThat(plan.size())
                    .as("переводов для %s", balances)
                    .isLessThanOrEqualTo((int) Math.max(nonZero - 1, 0));

            assertThat(plan).as("суммы переводов для %s", balances)
                    .allSatisfy(transfer -> assertThat(transfer.amount()).isPositive());

            assertThat(plan).as("перевод самому себе в %s", balances)
                    .allSatisfy(transfer -> assertThat(transfer.from()).isNotEqualTo(transfer.to()));

            assertThat(applyPlan(balances, plan))
                    .as("остаток после применения плана для %s", balances)
                    .allSatisfy((userId, balance) -> assertThat(balance).isEqualByComparingTo("0.00"));
        }
    }

    // ---------- вспомогательное ----------

    /** Применяет план к сальдо: отправитель уменьшает долг, получатель — своё требование. */
    private static Map<UUID, BigDecimal> applyPlan(List<BalanceSummary> balances, List<Transfer> plan) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        balances.forEach(b -> result.merge(b.userId(), b.balance(), BigDecimal::add));
        plan.forEach(transfer -> {
            result.merge(transfer.from(), transfer.amount(), BigDecimal::add);
            result.merge(transfer.to(), transfer.amount().negate(), BigDecimal::add);
        });
        return result;
    }

    private static List<BalanceSummary> randomZeroSumBalances(int count, Random random) {
        List<BalanceSummary> balances = new ArrayList<>();
        long totalCents = 0;
        for (int i = 0; i < count - 1; i++) {
            long cents = random.nextInt(40_001) - 20_000;
            totalCents += cents;
            balances.add(new BalanceSummary(userId(i + 1), "user" + (i + 1), BigDecimal.valueOf(cents, 2)));
        }
        // последний участник забирает остаток, чтобы сумма была ровно нулевой
        balances.add(new BalanceSummary(userId(count), "user" + count, BigDecimal.valueOf(-totalCents, 2)));
        return balances;
    }

    private static List<BalanceSummary> balances(String... amounts) {
        List<BalanceSummary> balances = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            balances.add(balance(userId(i + 1), amounts[i]));
        }
        return List.copyOf(balances);
    }

    private static BalanceSummary balance(UUID userId, String amount) {
        return new BalanceSummary(userId, "user", new BigDecimal(amount));
    }

    private static UUID userId(int index) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index));
    }
}
