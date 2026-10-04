package dev.toniess.splitly.settlement.exception;

import dev.toniess.splitly.common.exception.ConflictException;

import java.math.BigDecimal;

public class OutstandingBalanceException extends ConflictException {
    public OutstandingBalanceException(String message) {
        super(message);
    }

    public static OutstandingBalanceException ofAmount(BigDecimal amount) {
        return new OutstandingBalanceException("You have an outstanding balance with amount of " + amount);
    }
}
