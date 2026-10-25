package dev.toniess.splitly.common;

import dev.toniess.splitly.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ограничивает частоту запросов на вход по адресу клиента.
 *
 * <p>Защищает не от подбора пароля, а от исчерпания процессора: проверка пароля
 * через BCrypt с cost 12 стоит около четверти секунды вычислений, и десятка
 * одновременных запросов хватает, чтобы положить небольшой контейнер. Взлом для
 * этого не нужен, поэтому считаются <b>все</b> попытки, а не только неудачные.
 *
 * <p>Счётчики живут в памяти процесса. Это осознанный выбор: писать строку в базу
 * на каждый запрос — странная защита от перегрузки, а Redis ради одной ручки
 * не стоит отдельной инфраструктуры. Плата за простоту: при нескольких
 * экземплярах лимит умножается на их число, а при перезапуске счётчики обнуляются.
 */
@Component
@Order(RequestIdFilter.ORDER + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String PROTECTED_PATH = "/api/v1/auth/login";

    /** Порог, после которого при очередном запросе выметаются истёкшие окна. */
    private static final int CLEANUP_THRESHOLD = 1_000;

    private record Window(Instant startedAt, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;
    private final ProblemDetails problemDetails;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimitProperties properties,
            ProblemDetails problemDetails,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.problemDetails = problemDetails;
        this.objectMapper = objectMapper;
    }

    /** Фильтр нужен только на входе — лента трат и балансы столько не стоят. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String client = request.getRemoteAddr();
        Instant now = Instant.now();
        
        Window window = windows.compute(client, (key, previous) -> {
            if (previous == null || expired(previous, now)) {
                return new Window(now, 1);
            }
            return new Window(previous.startedAt(), previous.count() + 1);
        });

        if (windows.size() > CLEANUP_THRESHOLD) {
            evictExpired(now);
        }

        if (window.count() > properties.attempts()) {
            reject(request, response, window, now);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean expired(Window window, Instant now) {
        return window.startedAt().plus(properties.window()).isBefore(now);
    }

    /**
     * Без чистки карта растёт на каждый новый адрес и живёт вечно — защита
     * от перегрузки сама стала бы утечкой памяти.
     */
    private void evictExpired(Instant now) {
        windows.values().removeIf(window -> expired(window, now));
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            Window window,
            Instant now
    ) throws IOException {
        long retryAfter = Math.max(
                1,
                window.startedAt().plus(properties.window()).getEpochSecond() - now.getEpochSecond());

        ProblemDetail problem = problemDetails.of(
                HttpStatus.TOO_MANY_REQUESTS,
                "Слишком много попыток",
                "Слишком много попыток входа. Повторите через " + retryAfter + " с.",
                "rate-limit");
        problem.setInstance(URI.create(request.getRequestURI()));

        log.warn("Превышен лимит попыток входа, адрес={}, попыток={}", request.getRemoteAddr(),
                window.count());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
