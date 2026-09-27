package dev.toniess.splitly.expense.split;

import dev.toniess.splitly.expense.entity.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Component
public class ExactSplitStrategy implements SplitStrategy {
    @Override
    public SplitType type() {
        return SplitType.EXACT;
    }

    @Override
    public List<ShareAmount> split(BigDecimal amount, List<SplitInput> participants) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount should be greater than 0, got " + amount);
        }

        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Participants should not be empty");
        }

        if (participants.stream().anyMatch((splitInput -> splitInput.value().compareTo(BigDecimal.ZERO) < 0))) {
            throw new IllegalArgumentException("Any share should not be less than 0");
        }

        if (!participants.stream().allMatch((splitInput -> splitInput.value().stripTrailingZeros().scale() <= 2))) {
            throw new IllegalArgumentException("All the values should have scale less than or equal to 2");
        }


        final List<ShareAmount> splitted = participants.stream()
                .sorted(Comparator.comparing(SplitInput::userId))
                .map(input -> new ShareAmount(input.userId(), input.value()))
                .toList();

        final BigDecimal sum = splitted.stream().map(ShareAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(sum) != 0) {
            throw new IllegalArgumentException(
                    "Sum of shares is " + sum + ", but expense amount is " + amount);
        }

        return splitted;
    }
}
