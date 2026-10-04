package dev.toniess.splitly.settlement;

import dev.toniess.splitly.settlement.dto.BalanceSummary;
import dev.toniess.splitly.settlement.dto.Transfer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@Component
public class TransferPlanner {

    public List<Transfer> plan(List<BalanceSummary> balances) {
        final List<BalanceSummary> sorted = balances.stream()
                .filter(b -> b.balance().compareTo(BigDecimal.ZERO) != 0)
                .sorted(Comparator.comparing(BalanceSummary::balance).thenComparing(BalanceSummary::userId))
                .toList();

        final BigDecimal[] remaining = sorted.stream().map(BalanceSummary::balance).toArray(BigDecimal[]::new);
        int l = 0, r = remaining.length - 1;

        final List<Transfer> plan = new ArrayList<>();
        while (l < r) {
            final BigDecimal possible = remaining[l].abs().min(remaining[r]);
            if (possible.compareTo(BigDecimal.ZERO) == 0) {
                break;
            }

            remaining[l] = remaining[l].add(possible);
            remaining[r] = remaining[r].subtract(possible);

            plan.add(new Transfer(sorted.get(l).userId(), sorted.get(r).userId(), possible));

            if (remaining[l].compareTo(BigDecimal.ZERO) == 0) {
                l++;
            }

            if (remaining[r].compareTo(BigDecimal.ZERO) == 0) {
                r--;
            }
        }

        return plan;
    }

}
