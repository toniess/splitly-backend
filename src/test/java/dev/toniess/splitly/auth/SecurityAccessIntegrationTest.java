package dev.toniess.splitly.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
 * Проверка доступа через HTTP: что закрыто без токена, что видно чужим,
 * и действительно ли чужие ресурсы выглядят как несуществующие.
 *
 * <p>Тесты идут через настоящую цепочку фильтров Spring Security, поэтому проверяют
 * именно то поведение, которое увидит клиент.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String antonAccess;
    private String antonRefresh;
    private UUID antonId;
    private String mariaAccess;

    @BeforeEach
    void registerTwoUsers() throws Exception {
        JsonNode anton = registerAndLogin("Антон");
        antonAccess = anton.get("accessToken").asString();
        antonRefresh = anton.get("refreshToken").asString();
        antonId = UUID.fromString(me(antonAccess).get("id").asString());

        mariaAccess = registerAndLogin("Мария").get("accessToken").asString();
    }

    // ---------- что закрыто ----------

    @Test
    @DisplayName("Без токена защищённый эндпоинт отвечает 401")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value("https://splitly.dev/errors/unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/v1/groups"));
    }

    @Test
    @DisplayName("Мусор вместо токена — 401")
    void rejectsGarbageToken() throws Exception {
        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer не-токен"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Подделанная подпись — 401")
    void rejectsTamperedToken() throws Exception {
        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + tamper(antonAccess)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Токен без префикса Bearer не принимается")
    void rejectsTokenWithoutBearerPrefix() throws Exception {
        mockMvc.perform(get("/api/v1/groups").header("Authorization", antonAccess))
                .andExpect(status().isUnauthorized());
    }

    // ---------- что открыто ----------

    @Test
    @DisplayName("Регистрация и вход доступны без токена")
    void authEndpointsArePublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("public@" + UUID.randomUUID() + ".dev", "Гость")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Проверка живости доступна без токена")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("С валидным токеном защищённый эндпоинт отвечает 200")
    void acceptsValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + antonAccess))
                .andExpect(status().isOk());
    }

    // ---------- чужие данные ----------

    @Test
    @DisplayName("Чужая группа по прямому id — 404, а не 403")
    void foreignGroupLooksMissing() throws Exception {
        UUID groupId = createGroup(antonAccess);

        mockMvc.perform(get("/api/v1/groups/" + groupId).header("Authorization", "Bearer " + mariaAccess))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://splitly.dev/errors/not-found"));
    }

    @Test
    @DisplayName("Чужая лента трат — 404")
    void foreignExpenseFeedLooksMissing() throws Exception {
        UUID groupId = createGroup(antonAccess);

        mockMvc.perform(get("/api/v1/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + mariaAccess))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Чужие балансы — 404")
    void foreignBalancesLookMissing() throws Exception {
        UUID groupId = createGroup(antonAccess);

        mockMvc.perform(get("/api/v1/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + mariaAccess))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Список групп содержит только свои")
    void groupListIsScopedToOwner() throws Exception {
        UUID groupId = createGroup(antonAccess);

        String foreignList = mockMvc.perform(get("/api/v1/groups")
                        .header("Authorization", "Bearer " + mariaAccess))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(foreignList).doesNotContain(groupId.toString());
    }

    // ---------- личность владельца токена ----------

    @Test
    @DisplayName("/users/me возвращает владельца токена, а не кого-то ещё")
    void meReturnsTokenOwner() throws Exception {
        assertThat(UUID.fromString(me(antonAccess).get("id").asString())).isEqualTo(antonId);
        assertThat(UUID.fromString(me(mariaAccess).get("id").asString())).isNotEqualTo(antonId);
    }

    @Test
    @DisplayName("/users/me не отдаёт хэш пароля")
    void meDoesNotLeakPasswordHash() throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + antonAccess))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("passwordHash").doesNotContain("$2a$");
    }

    // ---------- обновление токенов ----------

    @Test
    @DisplayName("Использованный refresh-токен больше не принимается")
    void rotatedRefreshTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh\":\"" + antonRefresh + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh\":\"" + antonRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Несуществующий refresh-токен отклоняется")
    void unknownRefreshTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Неверный пароль и несуществующий email дают одинаковый ответ")
    void loginFailuresAreIndistinguishable() throws Exception {
        String email = "exists-" + UUID.randomUUID() + "@test.dev";
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationBody(email, "Кто-то")));

        String wrongPassword = login(email, "completely-wrong")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = login("nobody-" + UUID.randomUUID() + "@test.dev", "password123")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(detailOf(wrongPassword)).isEqualTo(detailOf(unknownEmail));
    }

    // ---------- вспомогательное ----------

    private JsonNode registerAndLogin(String displayName) throws Exception {
        String email = "sec-" + UUID.randomUUID() + "@test.dev";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(email, displayName)))
                .andExpect(status().isCreated());

        String body = login(email, "password123")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private JsonNode me(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID createGroup(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Поездка\",\"currency\":\"UZS\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asString());
    }

    /** Меняет один символ в полезной нагрузке — подпись перестаёт сходиться. */
    private static String tamper(String token) {
        String[] parts = token.split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'e' ? 'f' : 'e';
        return parts[0] + "." + new String(payload) + "." + parts[2];
    }

    private String registrationBody(String email, String displayName) {
        return "{\"email\":\"%s\",\"password\":\"password123\",\"displayName\":\"%s\"}"
                .formatted(email, displayName);
    }

    private String detailOf(String body) {
        return objectMapper.readTree(body).get("detail").asString();
    }
}
