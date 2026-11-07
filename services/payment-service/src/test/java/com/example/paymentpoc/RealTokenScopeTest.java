package com.example.paymentpoc;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real bearer tokens through the real filter chain; scope is a space-delimited string as IdPs send it. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RealTokenScopeTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void p(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }
    static final String A = UUID.randomUUID().toString(), ADMIN = UUID.randomUUID().toString();
    // token format: "<sub>|<space-delimited scopes>"
    @TestConfiguration static class C { @Bean JwtDecoder d() { return t -> {
        String[] parts = TOKENS.get(t);
        return Jwt.withTokenValue(t).header("alg", "none").subject(parts[0])
            .claim("scope", parts[1]).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build(); }; } }
    @Autowired MockMvc mvc;
    static final Map<String, String[]> TOKENS = new java.util.concurrent.ConcurrentHashMap<>();
    String auth(String sub, String scopes) { String t = "t" + TOKENS.size(); TOKENS.put(t, new String[]{sub, scopes}); return "Bearer " + t; }

    @Test void scopes_and_admin_work_with_space_delimited_scope_claim() throws Exception {
        var r = mvc.perform(post("/v1/payments").header("Authorization", auth(A, "payments:read payments:write"))
            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":\"7.00\",\"currency\":\"USD\"}")).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(201);
        String id = r.getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        assertThat(mvc.perform(get("/v1/payments/" + id)).andReturn().getResponse().getStatus()).as("no token GET").isEqualTo(401);
        assertThat(mvc.perform(get("/v1/payments/" + id).header("Authorization", auth(A, "payments:write"))).andReturn().getResponse().getStatus()).as("write-only GET").isEqualTo(403);
        assertThat(mvc.perform(get("/v1/payments/" + id).header("Authorization", auth(ADMIN, "payments:read payments:admin"))).andReturn().getResponse().getStatus()).as("admin+read GET other's payment").isEqualTo(200);
        assertThat(mvc.perform(get("/v1/payments").header("Authorization", auth(ADMIN, "payments:read payments:admin"))).andReturn().getResponse().getContentAsString().contains(id)).as("admin+read list includes other's payment").isEqualTo(true);
        assertThat(mvc.perform(get("/v1/payments/" + id).header("Authorization", auth(ADMIN, "payments:admin"))).andReturn().getResponse().getStatus()).as("admin ONLY (no read) GET").isEqualTo(403);
        assertThat(mvc.perform(post("/v1/payments/" + id + "/cancel").header("Authorization", auth(A, "payments:read"))).andReturn().getResponse().getStatus()).as("read-only cancel").isEqualTo(403);
    }
}
