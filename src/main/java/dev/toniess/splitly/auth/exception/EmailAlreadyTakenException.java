package dev.toniess.splitly.auth.exception;

import dev.toniess.splitly.common.exception.ConflictException;

public class EmailAlreadyTakenException extends ConflictException {
    public EmailAlreadyTakenException(String email) {
        super("Email " + email + " is already taken. Try to sign-in");
    }
}
