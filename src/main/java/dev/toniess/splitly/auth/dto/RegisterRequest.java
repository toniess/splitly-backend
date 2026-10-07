package dev.toniess.splitly.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @Email
        @NotBlank
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotBlank
        @Size(min = 2, max = 30)
        String displayName
) {
    @Override
    public String toString() {
        return "email: " + email + ", displayName: " + displayName;
    }


}
