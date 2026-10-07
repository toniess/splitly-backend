package dev.toniess.splitly.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
    @Override
    public String toString() {
        return "email: " + email;
    }
}

