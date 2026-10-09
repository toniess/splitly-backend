package dev.toniess.splitly.common;

import dev.toniess.splitly.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Повтор запроса с тем же {@code Idempotency-Key} не должен создавать вторую трату.
 *
 * <p>Проверяется через настоящий HTTP-слой: заголовок, тело, коды ответов — всё то,
 * что увидит клиент при обрыве связи и повторной отправке.
 */

@AutoConfigureMockMvc
class IdempotencyIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String antonToken;
    private UUID antonId;
    private UUID groupId;

    @BeforeEach
    void setUp() throws Exception {
        antonToken = registerAndLogin("Антон");
        antonId = UUID.fromString(me(antonToken).get("id").asString());
        groupId = createGroup(antonToken);
    }

    // ---------- собственно идемпотентность ----------

    @Test
    @DisplayName("Повтор с тем же ключом возвращает прежний ответ и не создаёт вторую трату")
    void replayReturnsSameResponseWithoutCreatingDuplicate() throws Exception {
        String key = UUID.randomUUID().toString();

        String first = createExpense(key, expenseBody("300.33")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = createExpense(key, expenseBody("300.33")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).as("повтор обязан быть неотличим от первого ответа").isEqualTo(first);
        assertThat(feedSize()).as("трат в ленте").isEqualTo(1);
    }

    @Test
    @DisplayName("Три повтора подряд дают одну трату")
    void repeatedRetriesCreateSingleExpense() throws Exception {
        String key = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < 3; attempt++) {
            createExpense(key, expenseBody("120.00")).andExpect(status().isCreated());
        }

        assertThat(feedSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("Тот же ключ с другим телом — 409, трата не создаётся")
    void rejectsSameKeyWithDifferentBody() throws Exception {
        String key = UUID.randomUUID().toString();
        createExpense(key, expenseBody("300.33")).andExpect(status().isCreated());

        createExpense(key, expenseBody("999.99"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://splitly.dev/errors/conflict"));

        assertThat(feedSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("Разные ключи создают разные траты")
    void differentKeysCreateDifferentExpenses() throws Exception {
        createExpense(UUID.randomUUID().toString(), expenseBody("300.33")).andExpect(status().isCreated());
        createExpense(UUID.randomUUID().toString(), expenseBody("300.33")).andExpect(status().isCreated());

        assertThat(feedSize()).isEqualTo(2);
    }

    /**
     * Ключ принадлежит пользователю, а не эндпоинту: пара {@code (user_id, key)} уникальна.
     * Поэтому тот же ключ для другой группы — это переиспользование ключа, а не повтор.
     *
     * <p>Главное здесь — чего <em>не</em> произошло: вторая группа не получила в ответ
     * трату первой. Именно от этого защищает {@code scope} в хеше.
     */
    @Test
    @DisplayName("Тот же ключ для другой группы — 409, а не чужая трата в ответе")
    void rejectsSameKeyForAnotherGroup() throws Exception {
        UUID otherGroup = createGroup(antonToken);
        String key = UUID.randomUUID().toString();

        createExpense(groupId, key, expenseBody("300.33")).andExpect(status().isCreated());
        createExpense(otherGroup, key, expenseBody("300.33")).andExpect(status().isConflict());

        assertThat(feedSize(groupId)).as("трат в первой группе").isEqualTo(1);
        assertThat(feedSize(otherGroup)).as("вторая группа осталась пустой").isZero();
    }

    @Test
    @DisplayName("Ключ одного пользователя не влияет на другого")
    void keyIsScopedToUser() throws Exception {
        String mariaToken = registerAndLogin("Мария");
        joinGroup(mariaToken, inviteCodeOf(groupId));
        String key = UUID.randomUUID().toString();

        createExpense(groupId, key, expenseBody("300.33")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/groups/{groupId}/expenses", groupId)
                        .header("Authorization", "Bearer " + mariaToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseBody("300.33")))
                .andExpect(status().isCreated());

        assertThat(feedSize()).as("два разных пользователя — две траты").isEqualTo(2);
    }

    // ---------- валидация ----------

    @Test
    @DisplayName("Без заголовка Idempotency-Key — 400")
    void rejectsMissingHeader() throws Exception {
        mockMvc.perform(post("/api/v1/groups/{groupId}/expenses", groupId)
                        .header("Authorization", "Bearer " + antonToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseBody("300.33")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
    }

    @Test
    @DisplayName("Пустой Idempotency-Key — 400 с именем заголовка в ошибке")
    void rejectsBlankHeader() throws Exception {
        createExpense("   ", expenseBody("300.33"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
    }

    @Test
    @DisplayName("Невалидное тело по-прежнему даёт 400 с именами полей, а не 500")
    void reportsBodyViolationsAsBadRequest() throws Exception {
        String noDescription = """
                {"description":"","amount":"300.33","payerId":"%s",
                 "spentAt":"2026-10-02T10:00:00Z","splitType":"EQUAL",
                 "participants":[{"userId":"%s"}]}
                """.formatted(antonId, antonId);

        createExpense(UUID.randomUUID().toString(), noDescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://splitly.dev/errors/validation"))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    @DisplayName("limit вне допустимого диапазона — 400 с именем параметра")
    void reportsQueryParameterViolationAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/groups/{groupId}/expenses", groupId)
                        .param("limit", "0")
                        .header("Authorization", "Bearer " + antonToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    // ---------- вспомогательное ----------

    private org.springframework.test.web.servlet.ResultActions createExpense(String key, String body) throws Exception {
        return createExpense(groupId, key, body);
    }

    private org.springframework.test.web.servlet.ResultActions createExpense(UUID group, String key, String body)
            throws Exception {
        return mockMvc.perform(post("/api/v1/groups/{groupId}/expenses", group)
                .header("Authorization", "Bearer " + antonToken)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String expenseBody(String amount) {
        return """
                {"description":"Такси","amount":"%s","payerId":"%s",
                 "spentAt":"2026-10-02T10:00:00Z","splitType":"EQUAL",
                 "participants":[{"userId":"%s"}]}
                """.formatted(amount, antonId, antonId);
    }

    private int feedSize() throws Exception {
        return feedSize(groupId);
    }

    private int feedSize(UUID group) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups/{groupId}/expenses", group)
                        .header("Authorization", "Bearer " + antonToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("items").size();
    }

    private String registerAndLogin(String displayName) throws Exception {
        String email = "idem-" + UUID.randomUUID() + "@test.dev";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"%s"}
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private JsonNode me(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID createGroup(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Поездка\",\"currency\":\"UZS\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asString());
    }

    private String inviteCodeOf(UUID group) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups/{id}", group)
                        .header("Authorization", "Bearer " + antonToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("inviteCode").asString();
    }

    private void joinGroup(String token, String inviteCode) throws Exception {
        mockMvc.perform(post("/api/v1/groups/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"%s\"}".formatted(inviteCode)))
                .andExpect(status().isOk());
    }
}
