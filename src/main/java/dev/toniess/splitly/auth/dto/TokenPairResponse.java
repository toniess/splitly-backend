package dev.toniess.splitly.auth.dto;

public record TokenPairResponse(
        String accessToken,
        String refreshToken,
        String tokenType,     // "Bearer"
        long expiresIn        // секунды до истечения access-токена
) {

}