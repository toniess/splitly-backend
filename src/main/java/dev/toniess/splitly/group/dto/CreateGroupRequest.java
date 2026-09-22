package dev.toniess.splitly.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(

        @NotBlank @Size(max = 40)
        String name,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency

) {}