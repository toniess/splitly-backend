package dev.toniess.splitly.expense.split;

import dev.toniess.splitly.expense.entity.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@Component
public class ShareSplitStrategy implements SplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.SHARES;
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

        if (participants.stream().allMatch((splitInput -> splitInput.value().compareTo(BigDecimal.ZERO) <= 0))) {
            throw new IllegalArgumentException("At least one share should be greater than 0");
        }
        
        int n = participants.stream().map(SplitInput::value).reduce(BigDecimal.ZERO, BigDecimal::add).intValue();
        long exactAmount = amount.movePointRight(2).longValueExact();
        final List<SplitInput> sorted = participants.stream().sorted(Comparator.comparing(SplitInput::userId)).toList();


        final List<Long> distributedByBase = sorted.stream()
                .map(s -> s.value().intValueExact() * exactAmount / n)
                .toList();
        final long rem = exactAmount - distributedByBase.stream().reduce(0L, Long::sum);
        final List<ShareAmount> splitted = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            final long cents = distributedByBase.get(i) + (i < rem ? 1 : 0);
            splitted.add(new ShareAmount(sorted.get(i).userId(), BigDecimal.valueOf(cents, 2)));
        }

        final BigDecimal sum = splitted.stream().map(ShareAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(sum) != 0) {
            throw new IllegalStateException(
                    "Split produced " + sum + " instead of " + amount
                            + " for weights " + participants.stream().map(SplitInput::value).toList());
        }

        return splitted;
    }
}
