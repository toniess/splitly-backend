package dev.toniess.splitly.expense.split;

import dev.toniess.splitly.expense.entity.SplitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitStrategyRegistryTest {

    private final EqualSplitStrategy equal = new EqualSplitStrategy();
    private final ShareSplitStrategy shares = new ShareSplitStrategy();
    private final ExactSplitStrategy exact = new ExactSplitStrategy();

    @Test
    @DisplayName("Каждый тип деления разрешается в свою стратегию")
    void resolvesEveryTypeToItsOwnStrategy() {
        SplitStrategyRegistry registry = new SplitStrategyRegistry(List.of(equal, shares, exact));

        assertThat(registry.resolve(SplitType.EQUAL)).isSameAs(equal);
        assertThat(registry.resolve(SplitType.SHARES)).isSameAs(shares);
        assertThat(registry.resolve(SplitType.EXACT)).isSameAs(exact);
    }

    @Test
    @DisplayName("Разрешается каждое значение SplitType — ни одно не осталось без стратегии")
    void resolvesAllDeclaredSplitTypes() {
        SplitStrategyRegistry registry = new SplitStrategyRegistry(List.of(equal, shares, exact));

        for (SplitType type : SplitType.values()) {
            assertThat(registry.resolve(type))
                    .as("стратегия для типа %s", type)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("Две стратегии с одним типом — отказ при создании реестра")
    void rejectsDuplicateTypes() {
        SplitStrategy duplicate = new FakeStrategy(SplitType.EQUAL);

        assertThatThrownBy(() -> new SplitStrategyRegistry(List.of(equal, shares, exact, duplicate)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Не для всех типов есть стратегия — отказ при создании реестра")
    void rejectsIncompleteStrategySet() {
        assertThatThrownBy(() -> new SplitStrategyRegistry(List.of(equal)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Пустой список стратегий — отказ при создании реестра")
    void rejectsEmptyStrategyList() {
        assertThatThrownBy(() -> new SplitStrategyRegistry(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Подставная стратегия: нужна только чтобы заявить чужой тип и вызвать конфликт.
     * Метод split никогда не вызывается.
     */
    private record FakeStrategy(SplitType type) implements SplitStrategy {
        @Override
        public List<ShareAmount> split(BigDecimal amount, List<SplitInput> participants) {
            throw new UnsupportedOperationException("не используется в тесте");
        }
    }
}
