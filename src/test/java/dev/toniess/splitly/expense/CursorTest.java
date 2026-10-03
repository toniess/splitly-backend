package dev.toniess.splitly.expense;

import dev.toniess.splitly.expense.exception.InvalidCursorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    private static final UUID ID = UUID.fromString("47db2638-735a-4d66-b543-db3c3ba5adc3");

    @Test
    @DisplayName("Курсор кодируется и раскодируется без потерь")
    void survivesRoundTrip() {
        Cursor original = new Cursor(Instant.parse("2026-10-02T10:00:00Z"), ID);

        assertThat(Cursor.decode(original.encode())).isEqualTo(original);
    }

    @Test
    @DisplayName("Наносекунды не теряются при кодировании")
    void keepsSubSecondPrecision() {
        Cursor original = new Cursor(Instant.parse("2026-10-03T20:23:23.180058Z"), ID);

        assertThat(Cursor.decode(original.encode()).spentAt())
                .isEqualTo(Instant.parse("2026-10-03T20:23:23.180058Z"));
    }

    @Test
    @DisplayName("Курсор непрозрачен: в нём не видно ни даты, ни идентификатора")
    void isOpaque() {
        String encoded = new Cursor(Instant.parse("2026-10-02T10:00:00Z"), ID).encode();

        assertThat(encoded).doesNotContain("2026", ID.toString(), "|");
    }

    @Test
    @DisplayName("Курсор пригоден для URL: без символов, требующих экранирования")
    void isUrlSafe() {
        String encoded = new Cursor(Instant.now(), UUID.randomUUID()).encode();

        assertThat(encoded).matches("[A-Za-z0-9_=-]+");
    }

    @Test
    @DisplayName("Строка не в Base64 отклоняется")
    void rejectsNonBase64() {
        assertThatThrownBy(() -> Cursor.decode("не base64 !!!"))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    @DisplayName("Base64 без разделителя отклоняется")
    void rejectsMissingSeparator() {
        assertThatThrownBy(() -> Cursor.decode(base64("нет разделителя")))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    @DisplayName("Некорректная дата в курсоре отклоняется")
    void rejectsBrokenTimestamp() {
        assertThatThrownBy(() -> Cursor.decode(base64("2026-13-45T99:00:00Z|" + ID)))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    @DisplayName("Некорректный идентификатор в курсоре отклоняется")
    void rejectsBrokenId() {
        assertThatThrownBy(() -> Cursor.decode(base64("2026-10-02T10:00:00Z|не-uuid")))
                .isInstanceOf(InvalidCursorException.class);
    }

    private static String base64(String raw) {
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
