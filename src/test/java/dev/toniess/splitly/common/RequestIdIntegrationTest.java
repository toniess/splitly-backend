package dev.toniess.splitly.common;

import dev.toniess.splitly.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной идентификатор запроса: заголовок → MDC → тело ошибки.
 *
 * <p>Проверки нарочно устроены так, чтобы их нельзя было удовлетворить запасным вариантом
 * в {@link ProblemDetails}: он подставляет случайный UUID, когда MDC пуст, и поэтому
 * «непустой traceId в ответе» ничего не доказывает. Доказывает только совпадение
 * с тем, что клиент прислал сам.
 */

@AutoConfigureMockMvc
class RequestIdIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String HEADER = "X-Request-Id";

    @Test
    @DisplayName("Присланный X-Request-Id доезжает до тела ошибки без изменений")
    void propagatesIncomingRequestIdIntoProblemDetail() throws Exception {
        String incoming = "client-id-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(get("/api/v1/groups").header(HEADER, incoming))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HEADER, incoming))
                .andReturn();

        String traceId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("traceId").asString();

        assertThat(traceId)
                .as("traceId в ответе обязан быть тем же, что прислал клиент, а не свежим UUID")
                .isEqualTo(incoming);
    }

    @Test
    @DisplayName("Без X-Request-Id сервер заводит свой и возвращает его в заголовке")
    void generatesRequestIdWhenHeaderIsAbsent() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String returned = result.getResponse().getHeader(HEADER);
        assertThat(returned).as("заголовок ответа").isNotBlank();

        String traceId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("traceId").asString();

        assertThat(traceId)
                .as("тот же идентификатор, что в заголовке — иначе клиенту нечего назвать в поддержке")
                .isEqualTo(returned);
    }

    @Test
    @DisplayName("Пустой X-Request-Id не попадает в ответ — сервер заводит свой")
    void replacesBlankRequestId() throws Exception {
        assertThat(idAssignedFor("   ")).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    @DisplayName("Слишком длинный X-Request-Id отбрасывается")
    void rejectsOverlongRequestId() throws Exception {
        String overlong = "a".repeat(65);

        assertThat(idAssignedFor(overlong))
                .as("иначе чужая строка любой длины попадала бы в каждую строку лога")
                .isNotEqualTo(overlong);
    }

    @Test
    @DisplayName("X-Request-Id с посторонними символами отбрасывается")
    void rejectsRequestIdWithUnexpectedCharacters() throws Exception {
        for (String hostile : List.of("id с пробелом", "id\tтабуляция", "кириллица", "id;rm -rf")) {
            assertThat(idAssignedFor(hostile))
                    .as("отклонено: %s", hostile)
                    .isNotEqualTo(hostile);
        }
    }

    @Test
    @DisplayName("Идентификатор ровно на границе длины принимается")
    void acceptsIdAtLengthLimit() throws Exception {
        String atLimit = "b".repeat(64);

        assertThat(idAssignedFor(atLimit)).isEqualTo(atLimit);
    }

    @Test
    @DisplayName("Успешный запрос тоже получает X-Request-Id")
    void returnsRequestIdOnSuccessfulResponse() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HEADER));
    }

    @Test
    @DisplayName("Два запроса получают разные идентификаторы")
    void assignsDistinctIdsToDistinctRequests() throws Exception {
        String first = mockMvc.perform(get("/api/v1/groups")).andReturn().getResponse().getHeader(HEADER);
        String second = mockMvc.perform(get("/api/v1/groups")).andReturn().getResponse().getHeader(HEADER);

        assertThat(first).isNotEqualTo(second);
    }

    /** Возвращает идентификатор, который сервер фактически присвоил запросу. */
    private String idAssignedFor(String incoming) throws Exception {
        return mockMvc.perform(get("/api/v1/groups").header(HEADER, incoming))
                .andReturn().getResponse().getHeader(HEADER);
    }

    @Test
    @DisplayName("После запроса MDC чист — идентификатор не утекает на следующий")
    void clearsMdcAfterRequest() throws Exception {
        mockMvc.perform(get("/api/v1/groups").header(HEADER, "утечка-" + UUID.randomUUID()));

        assertThat(org.slf4j.MDC.get(RequestIdFilter.TRACE_ID))
                .as("MDC обязан быть очищен в finally")
                .isNull();
    }
}
