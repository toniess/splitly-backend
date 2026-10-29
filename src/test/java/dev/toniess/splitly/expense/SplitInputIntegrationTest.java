package dev.toniess.splitly.expense;

import dev.toniess.splitly.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Кривые доли участников — ошибка клиента, а не сервера.
 *
 * <p>Стратегии деления раньше бросали на таком вводе {@code IllegalArgumentException},
 * {@code ArithmeticException} или NPE, и клиент получал 500 «внутренняя ошибка»
 * без объяснения. Ответ обязан быть 422 с понятной причиной.
 */
@AutoConfigureMockMvc
class SplitInputIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String token;
    private UUID userId;
    private UUID groupId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "split-" + UUID.randomUUID() + "@test.dev";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Антон"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        token = json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asString();
        userId = UUID.fromString(json(mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).get("id").asString());
        groupId = UUID.fromString(json(mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Поездка\",\"currency\":\"UZS\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asString());
    }

    @ParameterizedTest(name = "{0}: {1}")
    @DisplayName("Кривые доли — 422, а не 500")
    @CsvSource(delimiter = '|', textBlock = """
            SHARES | "value":"1.5"
            SHARES | "value":"0"
            SHARES | "value":null
            SHARES |
            EXACT  | "value":null
            EXACT  |
            """)
    void rejectsInvalidShareValues(String splitType, String value) throws Exception {
        createExpense(splitType, value == null ? "" : "," + value)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://splitly.dev/errors/unprocessable"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @ParameterizedTest(name = "{0}: {1}")
    @DisplayName("Корректные доли по-прежнему принимаются")
    @CsvSource(delimiter = '|', textBlock = """
            SHARES | "value":"2"
            SHARES | "value":"2.00"
            EXACT  | "value":"100.00"
            EQUAL  |
            """)
    void acceptsValidShareValues(String splitType, String value) throws Exception {
        createExpense(splitType, value == null ? "" : "," + value)
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions createExpense(String splitType, String value)
            throws Exception {
        return mockMvc.perform(post("/api/v1/groups/{groupId}/expenses", groupId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"description":"Такси","amount":"100.00","payerId":"%s",
                         "spentAt":"2026-10-02T10:00:00Z","splitType":"%s",
                         "participants":[{"userId":"%s"%s}]}
                        """.formatted(userId, splitType, userId, value)));
    }

    private tools.jackson.databind.JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
