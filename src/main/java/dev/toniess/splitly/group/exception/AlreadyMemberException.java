package dev.toniess.splitly.group.exception;

import dev.toniess.splitly.common.exception.ConflictException;

import java.util.UUID;

public class AlreadyMemberException extends ConflictException {
    public AlreadyMemberException(UUID userId, UUID groupId) {
        super("User with id " + userId + " is already in group with id " + groupId);
    }
}
