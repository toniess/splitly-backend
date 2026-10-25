package dev.toniess.splitly.common;

import dev.toniess.splitly.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ограничение частоты запросов на вход.
 *
 * <p>Лимит опущен до трёх попыток: проверяем поведение, а не конкретное число
 * из настроек, и не хочется гонять BCrypt двадцать раз на каждый тест.
 */

@AutoConfigureMockMvc
@TestPropertySource(properties = {"rate-limit.login.attempts=3", "rate-limit.login.window=1m"})
class RateLimitIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("Попытки сверх лимита отклоняются с 429")
    void rejectsAttemptsBeyondLimit() throws Exception {
        String ip = uniqueIp();

        for (int attempt = 1; attempt <= 3; attempt++) {
            // пароль неверный, но это неважно: считаются все попытки,
            // потому что процессор тратится в любом случае
            login(ip).andExpect(status().isUnauthorized());
        }

        login(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("В отказе есть Retry-After и формат ошибки тот же, что у остальных")
    void rejectionCarriesRetryAfterAndProblemDetail() throws Exception {
        String ip = uniqueIp();
        for (int attempt = 0; attempt < 3; attempt++) login(ip);

        MvcResult result = login(ip).andExpect(status().isTooManyRequests()).andReturn();

        assertThat(result.getResponse().getHeader("Retry-After"))
                .as("клиенту надо сказать, когда повторять")
                .isNotNull();
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(result.getResponse().getContentAsString())
                .contains("rate-limit")
                .contains("traceId");
    }

    @Test
    @DisplayName("Лимит считается по адресу: соседям он не мешает")
    void limitsPerClientAddress() throws Exception {
        String blocked = uniqueIp();
        for (int attempt = 0; attempt < 4; attempt++) login(blocked);
        login(blocked).andExpect(status().isTooManyRequests());

        // другой адрес начинает со своего счётчика
        login(uniqueIp()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Ограничение не распространяется на остальные ручки")
    void doesNotTouchOtherEndpoints() throws Exception {
        String ip = uniqueIp();
        for (int attempt = 0; attempt < 5; attempt++) login(ip);
        login(ip).andExpect(status().isTooManyRequests());

        // та же машина спокойно ходит в другие ручки — там нет дорогой проверки пароля
        mockMvc.perform(withIp(post("/api/v1/auth/register"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Одновременные попытки считаются без потерь")
    void countsConcurrentAttemptsWithoutLosing() throws Exception {
        String ip = uniqueIp();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        try {
            // Восемь запросов разом при лимите в три. Если счётчик обновлять
            // чтением и записью вместо атомарного compute, часть попыток
            // потеряется и разрешённых окажется больше трёх.
            List<Future<Integer>> futures = IntStream.range(0, 8)
                    .mapToObj(i -> pool.submit(statusOf(start, ip)))
                    .toList();

            start.countDown();

            int allowed = 0;
            int rejected = 0;
            for (Future<Integer> future : futures) {
                int status = future.get(20, TimeUnit.SECONDS);
                if (status == 429) rejected++;
                else allowed++;
            }

            assertThat(allowed).as("разрешено (лимит 3)").isEqualTo(3);
            assertThat(rejected).as("отклонено").isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- вспомогательное ----------

    private Callable<Integer> statusOf(CountDownLatch start, String ip) {
        return () -> {
            start.await();
            return login(ip).andReturn().getResponse().getStatus();
        };
    }

    private org.springframework.test.web.servlet.ResultActions login(String ip) throws Exception {
        return mockMvc.perform(withIp(post("/api/v1/auth/login"), ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.dev\",\"password\":\"wrong-password\"}"));
    }

    private static MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    /**
     * Свой адрес на каждый тест: счётчики живут в памяти приложения и переживают
     * границы тестов, поэтому общий адрес связал бы их друг с другом.
     */
    private static String uniqueIp() {
        int n = COUNTER.incrementAndGet();
        return "10.%d.%d.%d".formatted((n >> 16) & 0xFF, (n >> 8) & 0xFF, n & 0xFF);
    }

    private static final java.util.concurrent.atomic.AtomicInteger COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();

    private static String registration() {
        return """
                {"email":"rate-%s@test.dev","password":"password123","displayName":"Тест"}
                """.formatted(UUID.randomUUID());
    }
}
