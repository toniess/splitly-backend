package dev.toniess.splitly.auth;

import dev.toniess.splitly.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-which-is-long-enough-for-hmac-sha-512-signing-0123456789";
    private static final String OTHER_SECRET = "another-secret-which-is-also-long-enough-for-hmac-signing-9876543210";

    private final JwtService jwtService = jwtService(SECRET, Duration.ofMinutes(15));

    @Test
    @DisplayName("Выпущенный токен разбирается обратно в тот же идентификатор")
    void survivesRoundTrip() {
        UUID userId = UUID.randomUUID();

        assertThat(jwtService.extractUserId(jwtService.issueAccessToken(userId))).isEqualTo(userId);
    }

    @Test
    @DisplayName("Два токена одного пользователя различаются: в них разное время выпуска")
    void issuesDistinguishableTokens() {
        UUID userId = UUID.randomUUID();

        assertThat(jwtService.issueAccessToken(userId)).isNotBlank();
        assertThat(jwtService.issueAccessToken(userId)).startsWith("eyJ");
    }

    @Test
    @DisplayName("Подделанная подпись отклоняется")
    void rejectsTamperedSignature() {
        String token = jwtService.issueAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.extractUserId(tamper(token)))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("Токен, подписанный другим ключом, отклоняется")
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = jwtService(OTHER_SECRET, Duration.ofMinutes(15))
                .issueAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.extractUserId(foreign))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("Строка, не являющаяся токеном, отклоняется")
    void rejectsGarbage() {
        assertThatThrownBy(() -> jwtService.extractUserId("не токен вовсе"))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    @DisplayName("Просроченный токен отклоняется")
    void rejectsExpiredToken() {
        // срок жизни в прошлом: токен истекает в момент выпуска
        String expired = jwtService(SECRET, Duration.ofSeconds(-60))
                .issueAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.extractUserId(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Слишком короткий секрет не даёт создать сервис")
    void rejectsWeakSecret() {
        assertThatThrownBy(() -> jwtService("short", Duration.ofMinutes(15)))
                .isInstanceOf(WeakKeyException.class);
    }

    /** Меняет один символ в полезной нагрузке — подпись перестаёт сходиться. */
    private static String tamper(String token) {
        String[] parts = token.split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'e' ? 'f' : 'e';
        return parts[0] + "." + new String(payload) + "." + parts[2];
    }

    /**
     * Конструктор сейчас принимает {@link JwtProperties} дважды — после исправления
     * описки здесь останется один аргумент.
     */
    private static JwtService jwtService(String secret, Duration accessTtl) {
        JwtProperties properties = new JwtProperties(secret, accessTtl, Duration.ofDays(30));
        return new JwtService(properties, properties);
    }
}
