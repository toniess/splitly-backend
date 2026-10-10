package dev.toniess.splitly.config;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Настройки выпуска токенов.
 *
 * <p>Проверка длины секрета здесь не формальность. Spring Boot при привязке
 * {@code @ConfigurationProperties} не считает неразрешимый плейсхолдер ошибкой:
 * если переменной окружения нет, в поле попадает буквальная строка
 * {@code ${JWT_SECRET}}. Приложение с таким «секретом» поднимется и будет
 * подписывать токены общеизвестным значением.
 *
 * <p>Без этой проверки сбой проявлялся как {@code WeakKeyException} из недр
 * jjwt — по сообщению не догадаться, что дело в ненастроенном окружении.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(

        // 64 байта — минимум для HS512, и заодно отсекает неразрешённый плейсхолдер
        @NotNull
        @Size(min = 64, message = "секрет короче 64 байт; задана ли переменная JWT_SECRET?")
        String secret,

        @NotNull
        Duration accessTtl,

        @NotNull
        Duration refreshTtl
) {}
