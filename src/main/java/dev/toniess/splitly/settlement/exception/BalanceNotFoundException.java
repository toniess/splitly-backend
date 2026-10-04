package dev.toniess.splitly.settlement.exception;

import dev.toniess.splitly.common.exception.NotFoundException;

import java.util.UUID;

public class BalanceNotFoundException extends NotFoundException {
    public BalanceNotFoundException(UUID userId, UUID groupId) {
        super("Balance for user " + userId + " in group " + groupId + " was not found");
    }
}
