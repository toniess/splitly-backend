package dev.toniess.splitly.expense;

import dev.toniess.splitly.expense.exception.InvalidCursorException;
import jakarta.validation.constraints.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

public record Cursor(Instant spentAt, UUID id) {
    public String encode() {
        final String join = spentAt + "|" + id;
        return Base64.getUrlEncoder().encodeToString(join.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(@NotNull String raw) {
        try {
            final String join = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            final String[] parts = join.split("\\|");
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException | ArrayIndexOutOfBoundsException ex) {
            throw new InvalidCursorException("Некорректный курсор", ex.getCause());
        }
    }
}
