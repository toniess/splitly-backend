package dev.toniess.splitly.expense.split;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareSplitStrategyTest {

    private final ShareSplitStrategy strategy = new ShareSplitStrategy();

    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID U3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("100.00 в пропорции 1:1:2 — 25.00 + 25.00 + 50.00")
    void splitsProportionallyWithoutRemainder() {
        List<ShareAmount> shares = strategy.split(new BigDecimal("100.00"), weights(1, 1, 2));

        assertThat(shares).hasSize(3);
        assertThat(shares.get(0).amount()).isEqualByComparingTo("25.00");
        assertThat(shares.get(1).amount()).isEqualByComparingTo("25.00");
        assertThat(shares.get(2).amount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("10.00 в пропорции 1:2 — остаток в копейку достаётся первому")
    void givesSingleRemainderCentToFirstParticipant() {
        List<ShareAmount> shares = strategy.split(new BigDecimal("10.00"), weights(1, 2));

        assertThat(shares.get(0).amount()).isEqualByComparingTo("3.34");
        assertThat(shares.get(1).amount()).isEqualByComparingTo("6.66");
    }

    @Test
    @DisplayName("1.02 в пропорции 4:3:3 — две копейки остатка раздаются по одной")
    void spreadsRemainderOneCentPerParticipant() {
        List<ShareAmount> shares = strategy.split(new BigDecimal("1.02"), weights(4, 3, 3));

        // точные доли: 40.8, 30.6, 30.6 копеек
        // базы вниз:   40,   30,   30  → остаток 2 копейки первым двум
        assertThat(shares.get(0).amount()).isEqualByComparingTo("0.41");
        assertThat(shares.get(1).amount()).isEqualByComparingTo("0.31");
        assertThat(shares.get(2).amount()).isEqualByComparingTo("0.30");
    }

    @Test
    @DisplayName("Равные веса дают тот же результат, что и деление поровну")
    void behavesLikeEqualSplitWhenWeightsAreEqual() {
        List<ShareAmount> shares = strategy.split(new BigDecimal("100.00"), weights(1, 1, 1));

        assertThat(shares.get(0).amount()).isEqualByComparingTo("33.34");
        assertThat(shares.get(1).amount()).isEqualByComparingTo("33.33");
        assertThat(shares.get(2).amount()).isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("Крупные веса: 1000.00 в пропорции 7:3")
    void splitsWithLargerWeights() {
        List<ShareAmount> shares = strategy.split(new BigDecimal("1000.00"), weights(7, 3));

        assertThat(shares.get(0).amount()).isEqualByComparingTo("700.00");
        assertThat(shares.get(1).amount()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("Нулевой вес: участник остаётся в списке с долей 0.00")
    void keepsParticipantWithZeroWeight() {
        List<ShareAmount> shares = strategy.split(new BigDecimal("100.00"), weights(0, 1, 1));

        assertThat(shares).hasSize(3);
        assertThat(shares.get(0).amount()).isEqualByComparingTo("0.00");
        assertThat(shares.get(1).amount()).isEqualByComparingTo("50.00");
        assertThat(shares.get(2).amount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Порядок участников на входе не влияет на результат")
    void producesSameResultRegardlessOfInputOrder() {
        List<SplitInput> shuffled = List.of(
                new SplitInput(U3, BigDecimal.valueOf(2)),
                new SplitInput(U1, BigDecimal.valueOf(1)),
                new SplitInput(U2, BigDecimal.valueOf(1)));

        List<ShareAmount> shares = strategy.split(new BigDecimal("100.00"), shuffled);

        assertThat(shares.get(0).userId()).isEqualTo(U1);
        assertThat(shares.get(0).amount()).isEqualByComparingTo("25.00");
        assertThat(shares.get(1).userId()).isEqualTo(U2);
        assertThat(shares.get(2).userId()).isEqualTo(U3);
        assertThat(shares.get(2).amount()).isEqualByComparingTo("50.00");
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
        assertThatThrownBy(() -> strategy.split(BigDecimal.ZERO, weights(1, 1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> strategy.split(new BigDecimal("-10.00"), weights(1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Отрицательный вес отклоняется")
    void rejectsNegativeWeight() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), weights(1, -2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Все веса нулевые — делить не на что, отказ")
    void rejectsAllZeroWeights() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), weights(0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Инвариант: сумма долей равна сумме траты и каждая доля близка к точной")
    void everyShareIsWithinOneCentOfExactValue() {
        Random random = new Random(20260927);

        for (int iteration = 0; iteration < 1_000; iteration++) {
            long totalCents = 1 + random.nextInt(100_000_000);
            BigDecimal amount = BigDecimal.valueOf(totalCents, 2);
            int participantCount = 1 + random.nextInt(10);

            List<SplitInput> participants = new ArrayList<>();
            for (int i = 0; i < participantCount; i++) {
                participants.add(new SplitInput(
                        new UUID(random.nextLong(), random.nextLong()),
                        BigDecimal.valueOf(1 + random.nextInt(10))));
            }

            List<ShareAmount> shares = strategy.split(amount, participants);
            String context = "%s на веса %s".formatted(amount, weightsOf(participants));

            assertThat(shares).as("число долей: %s", context).hasSize(participantCount);
            assertThat(sumOf(shares)).as("сумма долей: %s", context).isEqualByComparingTo(amount);

            BigDecimal totalWeight = sumOfWeights(participants);
            for (ShareAmount share : shares) {
                BigDecimal weight = weightOf(participants, share.userId());
                BigDecimal exact = amount.multiply(weight)
                        .divide(totalWeight, 6, RoundingMode.HALF_UP);
                BigDecimal deviation = share.amount().subtract(exact).abs();

                // база округлена вниз, сверху добавляется не больше одной копейки остатка,
                // поэтому доля отстоит от точного значения максимум на копейку
                assertThat(deviation)
                        .as("отклонение доли %s (вес %s) от точной %s: %s",
                                share.amount(), weight, exact, context)
                        .isLessThanOrEqualTo(new BigDecimal("0.01"));
            }
        }
    }

    private static List<SplitInput> weights(int... values) {
        List<SplitInput> participants = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            UUID userId = UUID.fromString("00000000-0000-0000-0000-00000000000" + (i + 1));
            participants.add(new SplitInput(userId, BigDecimal.valueOf(values[i])));
        }
        return List.copyOf(participants);
    }

    private static BigDecimal sumOf(List<ShareAmount> shares) {
        return shares.stream()
                .map(ShareAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumOfWeights(List<SplitInput> participants) {
        return participants.stream()
                .map(SplitInput::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal weightOf(List<SplitInput> participants, UUID userId) {
        return participants.stream()
                .filter(participant -> participant.userId().equals(userId))
                .findFirst()
                .orElseThrow()
                .value();
    }

    private static List<BigDecimal> weightsOf(List<SplitInput> participants) {
        return participants.stream().map(SplitInput::value).toList();
    }
}
