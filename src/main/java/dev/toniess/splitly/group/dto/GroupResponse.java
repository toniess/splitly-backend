package dev.toniess.splitly.group.dto;

import dev.toniess.splitly.group.entity.Group;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(

        UUID id,
        String name,
        String currency,
        String inviteCode,
        Instant createdAt

) {
    public static GroupResponse from (Group group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getCurrency(),
                group.getInviteCode(),
                group.getCreatedAt()
        );
    }
}
