package com.example.paymentpoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end: create → get → cancel through the full HTTP stack against a real Postgres. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration
    static class SecurityTestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claim("sub", OWNER_ID.toString())
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID OWNER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private static RequestPostProcessor readJwt(UUID sub) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(sub.toString()))
                .authorities(new SimpleGrantedAuthority("SCOPE_payments:read"));
    }

    private static RequestPostProcessor writeJwt(UUID sub) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(sub.toString()))
                .authorities(new SimpleGrantedAuthority("SCOPE_payments:write"));
    }

    private static RequestPostProcessor readWriteJwt(UUID sub) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(sub.toString()))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_payments:read"),
                        new SimpleGrantedAuthority("SCOPE_payments:write"));
    }

    private static RequestPostProcessor adminReadJwt(UUID sub) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(sub.toString())
                        .claim("scope", List.of("payments:read", "payments:admin")))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_payments:read"),
                        new SimpleGrantedAuthority("SCOPE_payments:admin"));
    }

    private static RequestPostProcessor adminWriteJwt(UUID sub) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(sub.toString())
                        .claim("scope", List.of("payments:write", "payments:admin")))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_payments:write"),
                        new SimpleGrantedAuthority("SCOPE_payments:admin"));
    }

    private static RequestPostProcessor adminOnlyJwt(UUID sub) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(sub.toString())
                        .claim("scope", List.of("payments:admin")))
                .authorities(new SimpleGrantedAuthority("SCOPE_payments:admin"));
    }

    @Test
    void createGetCancel_full_flow() throws Exception {
        // Create (requires write)
        MvcResult createResult = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"100.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount").value("100.00"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String paymentId = objectMapper.readTree(responseBody).get("id").asText();

        // Get (requires read) — amount must match create (B9 regression)
        String getAmount = objectMapper.readTree(
                mockMvc.perform(get("/v1/payments/{id}", paymentId)
                                .with(readJwt(OWNER_ID)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(paymentId))
                        .andExpect(jsonPath("$.status").value("CREATED"))
                        .andReturn().getResponse().getContentAsString()
        ).get("amount").asText();
        assertThat(getAmount).isEqualTo("100.00");

        // Cancel (requires write)
        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId)
                        .with(writeJwt(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Cancel again should return 409
        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId)
                        .with(writeJwt(OWNER_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_STATE"));
    }

    @Test
    void create_then_replay_returns_201_same_payment_id() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = "{\"amount\":\"25.00\",\"currency\":\"EUR\"}";

        MvcResult first = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        MvcResult replay = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String replayId = objectMapper.readTree(replay.getResponse().getContentAsString()).get("id").asText();
        assertThat(replayId).isEqualTo(firstId);
    }

    @Test
    void same_key_different_body_returns_409() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"999.00\",\"currency\":\"EUR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_IDEMPOTENCY_KEY"));
    }

    @Test
    void get_other_owners_payment_returns_404() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10.00\",\"currency\":\"GBP\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        UUID otherId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        mockMvc.perform(get("/v1/payments/{id}", paymentId)
                        .with(readJwt(otherId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void same_key_different_owner_does_not_replay_cross_user() throws Exception {
        String sharedKey = UUID.randomUUID().toString();
        String body = "{\"amount\":\"1.00\",\"currency\":\"USD\"}";
        UUID otherOwner = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

        MvcResult first = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String firstOwnerId = objectMapper.readTree(first.getResponse().getContentAsString()).get("ownerId").asText();

        MvcResult second = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(otherOwner))
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
        String secondOwnerId = objectMapper.readTree(second.getResponse().getContentAsString()).get("ownerId").asText();

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(secondOwnerId).isEqualTo(otherOwner.toString());
        assertThat(firstOwnerId).isEqualTo(OWNER_ID.toString());
    }

    @Test
    void invalid_cursor_returns_400() throws Exception {
        mockMvc.perform(get("/v1/payments?cursor=@@@")
                        .with(readJwt(OWNER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void zero_amount_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"0\",\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missing_idempotency_key_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformed_json_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void non_uuid_path_returns_400() throws Exception {
        mockMvc.perform(get("/v1/payments/not-a-uuid")
                        .with(readJwt(OWNER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── Admin scope tests ──────────────────────────────────────────────────────

    @Test
    void admin_can_get_another_owners_payment() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"77.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        UUID adminId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        mockMvc.perform(get("/v1/payments/{id}", paymentId)
                        .with(adminReadJwt(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.ownerId").value(OWNER_ID.toString()));
    }

    @Test
    void admin_list_returns_all_owners_payments() throws Exception {
        UUID otherOwner = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID adminId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab");

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"11.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(otherOwner))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"22.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated());

        // Admin list must return items from both owners
        mockMvc.perform(get("/v1/payments")
                        .with(adminReadJwt(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void admin_can_cancel_another_owners_payment() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"55.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        UUID adminId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaac");

        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId)
                        .with(adminWriteJwt(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void write_only_scope_cannot_get() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"9.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // Write-only (no read, no admin) → 403
        mockMvc.perform(get("/v1/payments/{id}", paymentId)
                        .with(writeJwt(OWNER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void admin_only_scope_without_read_cannot_get_or_list() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"3.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        UUID adminId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaad");

        // admin scope alone (no payments:read) → 403 on GET by id
        mockMvc.perform(get("/v1/payments/{id}", paymentId)
                        .with(adminOnlyJwt(adminId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // admin scope alone (no payments:read) → 403 on list
        mockMvc.perform(get("/v1/payments")
                        .with(adminOnlyJwt(adminId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
