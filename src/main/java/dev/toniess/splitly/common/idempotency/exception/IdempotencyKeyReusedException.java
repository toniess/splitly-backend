package dev.toniess.splitly.common.idempotency.exception;

import dev.toniess.splitly.common.exception.ConflictException;

public class IdempotencyKeyReusedException extends ConflictException {
    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("Key " + idempotencyKey + " is already used for other request");
    }
}
