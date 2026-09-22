package dev.toniess.splitly.group.dto;

import dev.toniess.splitly.group.entity.Group;

import java.util.UUID;

public record GroupSummary(
        UUID id,
        String name,
        String currency
) {
    public static GroupSummary from (Group group) {
        return new GroupSummary(
                group.getId(),
                group.getName(),
                group.getCurrency()
        );
    }
}
