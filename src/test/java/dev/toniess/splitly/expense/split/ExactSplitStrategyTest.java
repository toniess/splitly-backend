package dev.toniess.splitly.expense.split;

import dev.toniess.splitly.expense.exception.SharesDoNotMatchAmountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExactSplitStrategyTest {

    private final ExactSplitStrategy strategy = new ExactSplitStrategy();

    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID U3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("Суммы сходятся — доли возвращаются как есть")
    void returnsSharesUnchangedWhenSumMatches() {
        List<ShareAmount> shares = strategy.split(
                new BigDecimal("4500.00"), amounts("1500.00", "1500.00", "1500.00"));

        assertThat(shares).hasSize(3);
        assertThat(shares.get(0).userId()).isEqualTo(U1);
        assertThat(shares.get(0).amount()).isEqualByComparingTo("1500.00");
        assertThat(shares.get(1).amount()).isEqualByComparingTo("1500.00");
        assertThat(shares.get(2).amount()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Недостача в одну копейку отклоняется")
    void rejectsSumSmallerThanAmountByOneCent() {
        assertThatThrownBy(() -> strategy.split(
                new BigDecimal("4500.00"), amounts("1500.00", "1500.00", "1499.99")))
                .isInstanceOf(SharesDoNotMatchAmountException.class);
    }

    @Test
    @DisplayName("Превышение суммы траты отклоняется")
    void rejectsSumGreaterThanAmount() {
        assertThatThrownBy(() -> strategy.split(
                new BigDecimal("4500.00"), amounts("2000.00", "1500.00", "1500.00")))
                .isInstanceOf(SharesDoNotMatchAmountException.class);
    }

    @Test
    @DisplayName("Нулевая доля допустима: за участника заплатили полностью другие")
    void allowsZeroShare() {
        List<ShareAmount> shares = strategy.split(
                new BigDecimal("100.00"), amounts("100.00", "0.00"));

        assertThat(shares).hasSize(2);
        assertThat(shares.get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(shares.get(1).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Отрицательная доля отклоняется, даже если сумма сходится")
    void rejectsNegativeShareEvenWhenSumMatches() {
        // 50 − 50 + 100 = 100: сумма верна, но доля отрицательной быть не может
        assertThatThrownBy(() -> strategy.split(
                new BigDecimal("100.00"), amounts("50.00", "-50.00", "100.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Доля с точностью мельче копейки отклоняется")
    void rejectsShareWithSubCentPrecision() {
        // 33.333 + 33.333 + 33.334 = 100.000 — сумма сходится, но таких денег не бывает
        assertThatThrownBy(() -> strategy.split(
                new BigDecimal("100.00"), amounts("33.333", "33.333", "33.334")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Порядок участников на входе не влияет на результат")
    void producesSameResultRegardlessOfInputOrder() {
        List<SplitInput> shuffled = List.of(
                new SplitInput(U3, new BigDecimal("10.00")),
                new SplitInput(U1, new BigDecimal("70.00")),
                new SplitInput(U2, new BigDecimal("20.00")));

        List<ShareAmount> shares = strategy.split(new BigDecimal("100.00"), shuffled);

        assertThat(shares.get(0).userId()).isEqualTo(U1);
        assertThat(shares.get(0).amount()).isEqualByComparingTo("70.00");
        assertThat(shares.get(1).userId()).isEqualTo(U2);
        assertThat(shares.get(2).userId()).isEqualTo(U3);
        assertThat(shares.get(2).amount()).isEqualByComparingTo("10.00");
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
        assertThatThrownBy(() -> strategy.split(BigDecimal.ZERO, amounts("0.00")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> strategy.split(new BigDecimal("-10.00"), amounts("-10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Стратегия обслуживает тип EXACT")
    void servesExactType() {
        assertThat(strategy.type()).isEqualTo(dev.toniess.splitly.expense.entity.SplitType.EXACT);
    }

    private static List<SplitInput> amounts(String... values) {
        List<SplitInput> participants = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            UUID userId = UUID.fromString("00000000-0000-0000-0000-00000000000" + (i + 1));
            participants.add(new SplitInput(userId, new BigDecimal(values[i])));
        }
        return List.copyOf(participants);
    }
}
