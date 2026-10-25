package dev.toniess.splitly.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Ограничение частоты запросов на вход.
 *
 * @param attempts сколько попыток разрешено с одного адреса за окно
 * @param window   длительность окна
 */
@Validated
@ConfigurationProperties(prefix = "rate-limit.login")
public record RateLimitProperties(

        @NotNull @Positive Integer attempts,

        @NotNull Duration window
) {}
