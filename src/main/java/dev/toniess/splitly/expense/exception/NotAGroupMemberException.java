package dev.toniess.splitly.expense.exception;

import dev.toniess.splitly.common.exception.UnprocessableException;

import java.util.Collection;
import java.util.UUID;

public class NotAGroupMemberException extends UnprocessableException {

    private NotAGroupMemberException(String message) {
        super(message);
    }

    public static NotAGroupMemberException user(UUID userId, UUID groupId) {
        return new NotAGroupMemberException(
                "User " + userId + " is not a member of group " + groupId);
    }

    public static NotAGroupMemberException payer(UUID payerId, UUID groupId) {
        return new NotAGroupMemberException(
                "Payer " + payerId + " is not a member of group " + groupId);
    }

    public static NotAGroupMemberException participants(Collection<UUID> userIds, UUID groupId) {
        return new NotAGroupMemberException(
                "Users " + userIds + " are not members of group " + groupId);
    }
}
