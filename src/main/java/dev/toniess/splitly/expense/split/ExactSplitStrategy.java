package dev.toniess.splitly.expense.split;

import dev.toniess.splitly.expense.entity.SplitType;
import dev.toniess.splitly.expense.exception.InvalidSplitException;
import dev.toniess.splitly.expense.exception.SharesDoNotMatchAmountException;
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

        // Ввод пользователя: ответ 422 с объяснением, а не 500
        if (participants.stream().anyMatch(splitInput -> splitInput.value() == null)) {
            throw new InvalidSplitException("При делении точными суммами у каждого участника должна быть сумма");
        }

        if (participants.stream().anyMatch((splitInput -> splitInput.value().compareTo(BigDecimal.ZERO) < 0))) {
            throw new InvalidSplitException("Сумма доли не может быть отрицательной");
        }

        if (!participants.stream().allMatch((splitInput -> splitInput.value().stripTrailingZeros().scale() <= 2))) {
            throw new InvalidSplitException("В сумме доли не больше двух знаков после точки");
        }


        final List<ShareAmount> splitted = participants.stream()
                .sorted(Comparator.comparing(SplitInput::userId))
                .map(input -> new ShareAmount(input.userId(), input.value()))
                .toList();

        final BigDecimal sum = splitted.stream().map(ShareAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(sum) != 0) {
            throw new SharesDoNotMatchAmountException(
                    "Sum of shares is " + sum + ", but expense amount is " + amount);
        }

        return splitted;
    }
}
