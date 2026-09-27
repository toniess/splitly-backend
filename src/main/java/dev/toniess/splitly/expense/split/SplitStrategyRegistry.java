package dev.toniess.splitly.expense.split;

import dev.toniess.splitly.expense.entity.SplitType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SplitStrategyRegistry {

    private final Map<SplitType, SplitStrategy> strategyMap = new EnumMap<>(SplitType.class);

    public SplitStrategyRegistry(List<SplitStrategy> strategies) {
        strategies.forEach(s -> {
            if (strategyMap.put(s.type(), s) != null) {
                throw new IllegalArgumentException("Each strategy should have unique type");
            }
        });
        if (!strategyMap.keySet().containsAll(EnumSet.allOf(SplitType.class))) {
            throw new IllegalArgumentException("Init Strategy list should contain all the existing strategies");
        }
    }

    public SplitStrategy resolve(SplitType type) {
        final SplitStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalStateException("Strategy with type " + type + " was not found");
        }

        return strategy;
    }

}
