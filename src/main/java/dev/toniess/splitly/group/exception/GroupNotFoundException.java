package dev.toniess.splitly.group.exception;

import dev.toniess.splitly.common.exception.NotFoundException;

import java.util.UUID;

public class GroupNotFoundException extends NotFoundException {

    private GroupNotFoundException(String message) {
        super(message);
    }

    public static GroupNotFoundException byId(UUID groupId) {
        return new GroupNotFoundException("Group with id " + groupId + " not found");
    }

    public static GroupNotFoundException byInviteCode(String inviteCode) {
        return new GroupNotFoundException("Group with invite code " + inviteCode + " not found");
    }
}
