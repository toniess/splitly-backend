package dev.toniess.splitly.user.exception;

import dev.toniess.splitly.common.exception.NotFoundException;

import java.util.UUID;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException(UUID userId) {
        super("User with id " + userId + " not found");
    }
}
