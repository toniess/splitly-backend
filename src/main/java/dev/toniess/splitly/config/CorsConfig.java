package dev.toniess.splitly.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Разрешения для браузерных запросов с другого источника.
 *
 * <p>Без этой настройки фронтенд не сделает ни одного запроса: браузер сам
 * отклонит ответ, даже если сервер его вернул. Серверные клиенты —
 * {@code curl}, мобильные приложения — CORS не касается, это защита
 * исключительно браузерная.
 */
@Configuration
public class CorsConfig {

    /**
     * Источники перечисляются явно: {@code *} несовместим с передачей
     * заголовка Authorization и, что важнее, открыл бы API любому сайту,
     * на который зайдёт пользователь.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        // Клиент читает X-Request-Id из ответа, чтобы показать его в сообщении об ошибке.
        // Без exposedHeaders браузер скроет заголовок даже при разрешённом запросе.
        config.setExposedHeaders(List.of("X-Request-Id"));
        // Сколько браузеру держать результат предполётного запроса, не повторяя его
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
