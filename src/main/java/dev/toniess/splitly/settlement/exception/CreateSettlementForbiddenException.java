package dev.toniess.splitly.settlement.exception;

import dev.toniess.splitly.common.exception.ForbiddenException;

public class CreateSettlementForbiddenException extends ForbiddenException {
    public CreateSettlementForbiddenException(String message) {
        super(message);
    }
}
