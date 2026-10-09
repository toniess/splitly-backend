package dev.toniess.splitly.common.idempotency.exception;

import dev.toniess.splitly.common.exception.ConflictException;

public class RequestInProgressException extends ConflictException {
    public RequestInProgressException(String idempotencyKey) {
        super("Request with key " + idempotencyKey + " is in progress");
    }
}
