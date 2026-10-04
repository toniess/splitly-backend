package dev.toniess.splitly.settlement;

import java.math.BigDecimal;
import java.util.UUID;

public interface BalanceProjection {
    UUID getUserId();
    String getDisplayName();
    BigDecimal getBalance();
}
