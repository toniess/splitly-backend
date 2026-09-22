package dev.toniess.splitly.group.dto;

import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberResponse(
        UUID userId,
        String displayName,
        GroupRole role,
        Instant joinedAt
) {
    public static GroupMemberResponse from(GroupMember groupMember) {
        return new GroupMemberResponse(
            groupMember.getUser().getId(),
            groupMember.getUser().getDisplayName(),
            groupMember.getRole(),
            groupMember.getJoinedAt()
        );
    }
}
