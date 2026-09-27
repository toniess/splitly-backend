package dev.toniess.splitly.expense.split;

import dev.toniess.splitly.expense.entity.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@Component
public class EqualSplitStrategy implements SplitStrategy {
    @Override
    public SplitType type() {
        return SplitType.EQUAL;
    }

    @Override
    public List<ShareAmount> split(BigDecimal amount, List<SplitInput> participants) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount should be greater than 0, got " + amount);
        }

        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Participants should not be empty");
        }

        final List<SplitInput> sorted = participants.stream()
                .sorted(Comparator.comparing(SplitInput::userId))
                .toList();

        int n = participants.size();
        long exactAmount = amount.movePointRight(2).longValueExact();
        long rem = exactAmount % n;
        long base = exactAmount / n;

        final List<ShareAmount> splitted = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            long cents = base + (i < rem ? 1 : 0);
            BigDecimal share = BigDecimal.valueOf(cents, 2);
            splitted.add(new ShareAmount(sorted.get(i).userId(), share));
        }


        final BigDecimal sum = splitted.stream().map(ShareAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(sum) != 0) {
            throw new IllegalStateException(
                    "Split produced " + sum + " instead of " + amount
                            + " for " + participants.size() + " participants");
        }

        return splitted;
    }
}
