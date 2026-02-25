package com.app.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.app.auth.service.AppAccessControlService;
import com.app.auth.service.AppInternalEventAuthService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "AUTH_DB_PATH=./target/test-auth.db",
    "AUTH_ADMIN_PASSWORD_BCRYPT=$2a$10$iRKcZMdyuNZ9SkqqmufY7eZ9MGLaYILiYlTaqrUDiFStJFNljYBdG",
    "AUTH_APP_MASTER_PASSWORD_BCRYPT=$2a$10$iRKcZMdyuNZ9SkqqmufY7eZ9MGLaYILiYlTaqrUDiFStJFNljYBdG"
})
@AutoConfigureMockMvc
class AppAuthInboxIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppInternalEventAuthService appInternalEventAuthService;

    @Autowired
    private AppAccessControlService appAccessControlService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM CHAT_EVENT_DEDUP_");
        jdbcTemplate.update("DELETE FROM INBOX_MESSAGE_");
        jdbcTemplate.update("DELETE FROM DEVICE_");
        appAccessControlService.setNewDeviceLoginAllowed(false);
    }

    @Test
    void loginRefreshAndRevokeShouldWork() throws Exception {
        appAccessControlService.setNewDeviceLoginAllowed(true);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "masterPassword":"password",
                      "deviceName":"iPhone 15"
                    }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.deviceToken").isNotEmpty())
            .andExpect(jsonPath("$.deviceId").isNotEmpty())
            .andReturn();

        JsonNode loginJson = readJson(loginResult);
        String accessToken = loginJson.path("accessToken").asText();
        String deviceToken = loginJson.path("deviceToken").asText();
        String deviceId = loginJson.path("deviceId").asText();

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId").value(deviceId));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceToken\":\"" + deviceToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(delete("/api/auth/devices/" + deviceId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedDeviceShouldNotRefresh() throws Exception {
        appAccessControlService.setNewDeviceLoginAllowed(true);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "masterPassword":"password",
                      "deviceName":"Pixel 9"
                    }
                """))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode loginJson = readJson(loginResult);
        String accessToken = loginJson.path("accessToken").asText();
        String deviceToken = loginJson.path("deviceToken").asText();
        String deviceId = loginJson.path("deviceId").asText();

        mockMvc.perform(delete("/api/auth/devices/" + deviceId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceToken\":\"" + deviceToken + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid device token"));
    }

    @Test
    void customAccessTtlShouldWorkWithinConfiguredLimit() throws Exception {
        appAccessControlService.setNewDeviceLoginAllowed(true);
        Instant beforeLogin = Instant.now();
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "masterPassword":"password",
                      "deviceName":"iPad Pro",
                      "accessTtlSeconds":1800
                    }
                """))
            .andExpect(status().isOk())
            .andReturn();
        Instant afterLogin = Instant.now();

        JsonNode loginJson = readJson(loginResult);
        Instant loginExpireAt = Instant.parse(loginJson.path("accessTokenExpireAt").asText());
        String deviceToken = loginJson.path("deviceToken").asText();

        org.assertj.core.api.Assertions.assertThat(loginExpireAt).isAfter(beforeLogin.plusSeconds(1700));
        org.assertj.core.api.Assertions.assertThat(loginExpireAt).isBefore(afterLogin.plusSeconds(1900));

        Instant beforeRefresh = Instant.now();
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceToken\":\"" + deviceToken + "\",\"accessTtlSeconds\":1200}"))
            .andExpect(status().isOk())
            .andReturn();
        Instant afterRefresh = Instant.now();

        Instant refreshExpireAt = Instant.parse(readJson(refreshResult).path("accessTokenExpireAt").asText());
        org.assertj.core.api.Assertions.assertThat(refreshExpireAt).isAfter(beforeRefresh.plusSeconds(1100));
        org.assertj.core.api.Assertions.assertThat(refreshExpireAt).isBefore(afterRefresh.plusSeconds(1300));
    }

    @Test
    void accessTtlLongerThanLimitShouldBeRejected() throws Exception {
        appAccessControlService.setNewDeviceLoginAllowed(true);
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "masterPassword":"password",
                      "deviceName":"iPad Pro",
                      "accessTtlSeconds":50000
                    }
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", Matchers.containsString("requested access ttl exceeds limit")));
    }

    @Test
    void adminSendInboxShouldBeVisibleForAppAndMarkRead() throws Exception {
        appAccessControlService.setNewDeviceLoginAllowed(true);
        MockCookie adminCookie = adminLoginCookie();

        mockMvc.perform(post("/admin/api/inbox/send")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"系统通知",
                      "content":"发布成功",
                      "type":"INFO"
                    }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("系统通知"));

        MvcResult appLoginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "masterPassword":"password",
                      "deviceName":"MacBook"
                    }
                """))
            .andExpect(status().isOk())
            .andReturn();
        String accessToken = readJson(appLoginResult).path("accessToken").asText();

        mockMvc.perform(get("/api/app/inbox/unread-count")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(post("/api/app/inbox/read-all")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/app/inbox/unread-count")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void newDeviceLoginShouldBeForbiddenWhenOnboardingClosed() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "masterPassword":"password",
                      "deviceName":"Locked Device"
                    }
                """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("new device onboarding is disabled"));
    }

    @Test
    void newDeviceAccessStatusShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/auth/new-device-access"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowNewDeviceLogin").value(false));
    }

    @Test
    void refreshShouldStillWorkWhenNewDeviceOnboardingClosed() throws Exception {
        appAccessControlService.setNewDeviceLoginAllowed(true);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "masterPassword":"password",
                      "deviceName":"Known Device"
                    }
                """))
            .andExpect(status().isOk())
            .andReturn();

        String deviceToken = readJson(loginResult).path("deviceToken").asText();
        appAccessControlService.setNewDeviceLoginAllowed(false);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceToken\":\"" + deviceToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void chatEventsShouldDedupAndNotWriteInbox() throws Exception {
        long before = countInbox();
        String body = """
            {
              "chatId":"123e4567-e89b-12d3-a456-426614174111",
              "runId":"123e4567-e89b-12d3-a456-426614174112",
              "chatName":"Demo Chat"
            }
        """;
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = appInternalEventAuthService.sign(timestamp, body);

        mockMvc.perform(post("/api/app/internal/chat-events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-App-Timestamp", timestamp)
                .header("X-App-Signature", signature)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(true))
            .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/api/app/internal/chat-events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-App-Timestamp", timestamp)
                .header("X-App-Signature", signature)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(true))
            .andExpect(jsonPath("$.duplicate").value(true));

        long after = countInbox();
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
    }

    @Test
    void chatEventsWithInvalidSignatureShouldBeUnauthorized() throws Exception {
        mockMvc.perform(post("/api/app/internal/chat-events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-App-Timestamp", String.valueOf(System.currentTimeMillis() / 1000))
                .header("X-App-Signature", "bad-signature")
                .content("""
                    {
                      "chatId":"123e4567-e89b-12d3-a456-426614174121",
                      "runId":"123e4567-e89b-12d3-a456-426614174122"
                    }
                """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void bcryptGenerateShouldBePublicAndReturnValidHash() throws Exception {
        String rawPassword = "my-plain-password";

        MvcResult result = mockMvc.perform(post("/admin/api/bcrypt/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + rawPassword + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bcrypt", Matchers.startsWith("$2")))
            .andReturn();

        String bcrypt = readJson(result).path("bcrypt").asText();
        org.assertj.core.api.Assertions.assertThat(new BCryptPasswordEncoder().matches(rawPassword, bcrypt)).isTrue();
    }

    private MockCookie adminLoginCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/api/session/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"admin",
                      "password":"password"
                    }
                """))
            .andExpect(status().isOk())
            .andReturn();
        MockCookie cookie = (MockCookie) result.getResponse().getCookie("ADMIN_SESSION");
        if (cookie == null) {
            throw new IllegalStateException("missing admin cookie");
        }
        return cookie;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long countInbox() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INBOX_MESSAGE_", Long.class);
        return count == null ? 0L : count;
    }
}
