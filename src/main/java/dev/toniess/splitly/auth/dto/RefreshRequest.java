package dev.toniess.splitly.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank
        String refresh
) {
}
