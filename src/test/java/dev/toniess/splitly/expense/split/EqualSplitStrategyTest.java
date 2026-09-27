package dev.toniess.splitly.expense.split;

import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EqualSplitStrategyTest {

    private final EqualSplitStrategy strategy = new EqualSplitStrategy();

    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID U3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID U4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID U5 = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID U6 = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID U7 = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Test
    @DisplayName("100.00 на троих: 33.34 + 33.33 + 33.33")
    void splitsHundredAmongThreeGivingRemainderCentToFirst() {
        List<ShareAmount> shares = strategy.split(new BigDecimal("100.00"), participants(U1, U2, U3));

        assertThat(shares).hasSize(3);
        assertThat(shares.getFirst().userId()).isEqualTo(U1);
        assertThat(shares.getFirst().amount()).isEqualByComparingTo("33.34");
        assertThat(shares.subList(1, 3)).allSatisfy(share ->
                assertThat(share.amount()).isEqualByComparingTo("33.33"));
    }

    @Test
    @DisplayName("4500.00 на троих: делится нацело, остатка нет")
    void splitsEvenlyWhenThereIsNoRemainder() {
        List<ShareAmount> shares = strategy.split(new BigDecimal("4500.00"), participants(U1, U2, U3));

        assertThat(shares).hasSize(3);
        assertThat(shares).allSatisfy(share ->
                assertThat(share.amount()).isEqualByComparingTo("1500.00"));
    }

    @Test
    @DisplayName("0.01 на двоих: одному копейка, другому ноль")
    void splitsSingleCentBetweenTwoParticipants() {
        List<ShareAmount> shares = strategy.split(new BigDecimal("0.01"), participants(U1, U2));

        assertThat(shares.get(0).amount()).isEqualByComparingTo("0.01");
        assertThat(shares.get(1).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("100.00 на семерых: остаток в 4 копейки раздан четверым")
    void spreadsRemainderOverSeveralParticipants() {
        List<ShareAmount> shares = strategy.split(
                new BigDecimal("100.00"), participants(U1, U2, U3, U4, U5, U6, U7));

        assertThat(shares).hasSize(7);
        assertThat(shares.subList(0, 4)).allSatisfy(share ->
                assertThat(share.amount()).isEqualByComparingTo("14.29"));
        assertThat(shares.subList(4, 7)).allSatisfy(share ->
                assertThat(share.amount()).isEqualByComparingTo("14.28"));
    }

    @Test
    @DisplayName("Порядок участников на входе не влияет на результат")
    void producesSameResultRegardlessOfInputOrder() {
        List<ShareAmount> shuffled = strategy.split(new BigDecimal("100.00"), participants(U3, U1, U2));

        assertThat(shuffled.get(0).userId()).isEqualTo(U1);
        assertThat(shuffled.get(0).amount()).isEqualByComparingTo("33.34");
        assertThat(shuffled.get(1).userId()).isEqualTo(U2);
        assertThat(shuffled.get(2).userId()).isEqualTo(U3);
    }

    @Test
    @DisplayName("Пустой список участников отклоняется")
    void rejectsEmptyParticipants() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Неположительная сумма отклоняется")
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> strategy.split(BigDecimal.ZERO, participants(U1, U2)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> strategy.split(new BigDecimal("-10.00"), participants(U1, U2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Инвариант: сумма долей равна сумме траты при любых входных данных")
    void sumOfSharesAlwaysEqualsAmount() {
        Random random = new Random(20260927);

        for (int iteration = 0; iteration < 1_000; iteration++) {
            long cents = 1 + random.nextInt(100_000_000);
            BigDecimal amount = BigDecimal.valueOf(cents, 2);
            int participantCount = 1 + random.nextInt(20);

            List<SplitInput> participants = randomParticipants(participantCount, random);
            List<ShareAmount> shares = strategy.split(amount, participants);

            assertThat(shares)
                    .as("число долей для %s на %d участников", amount, participantCount)
                    .hasSize(participantCount);

            assertThat(sumOf(shares))
                    .as("сумма долей для %s на %d участников", amount, participantCount)
                    .isEqualByComparingTo(amount);

            assertThat(shares).allSatisfy(share ->
                    assertThat(share.amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO));
        }
    }
    
    private static List<SplitInput> participants(UUID... userIds) {
        return java.util.Arrays.stream(userIds)
                .map(userId -> new SplitInput(userId, null))
                .toList();
    }

    private static List<SplitInput> randomParticipants(int count, Random random) {
        return java.util.stream.Stream.generate(
                        () -> new SplitInput(new UUID(random.nextLong(), random.nextLong()), null))
                .limit(count)
                .toList();
    }

    private static BigDecimal sumOf(List<ShareAmount> shares) {
        return shares.stream()
                .map(ShareAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
