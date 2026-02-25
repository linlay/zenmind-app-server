package com.app.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "AUTH_DB_PATH=./target/test-auth.db",
    "AUTH_ADMIN_PASSWORD_BCRYPT=$2a$10$iRKcZMdyuNZ9SkqqmufY7eZ9MGLaYILiYlTaqrUDiFStJFNljYBdG",
    "AUTH_APP_MASTER_PASSWORD_BCRYPT=$2a$10$iRKcZMdyuNZ9SkqqmufY7eZ9MGLaYILiYlTaqrUDiFStJFNljYBdG"
})
@AutoConfigureMockMvc
class AuthBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openIdConfigurationShouldBeExposed() throws Exception {
        mockMvc.perform(get("/openid/.well-known/openid-configuration"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:8080/oauth2/authorize"))
            .andExpect(jsonPath("$.userinfo_endpoint").value("http://localhost:8080/openid/userinfo"));
    }

    @Test
    void jwksEndpointsShouldExposeStandardJwkSet() throws Exception {
        mockMvc.perform(get("/openid/jwks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.publicKey").doesNotExist());

        mockMvc.perform(get("/api/auth/jwks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.publicKey").doesNotExist());
    }
}
