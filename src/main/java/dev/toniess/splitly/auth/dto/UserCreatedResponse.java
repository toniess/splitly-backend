package dev.toniess.splitly.auth.dto;

import dev.toniess.splitly.user.entity.User;

import java.util.UUID;

public record UserCreatedResponse(
        UUID userId
) {
    public static UserCreatedResponse fromUser(User user) {
        return new UserCreatedResponse(user.getId());
    }
}
