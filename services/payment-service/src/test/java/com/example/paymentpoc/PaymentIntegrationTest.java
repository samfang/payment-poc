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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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

    private Jwt jwt(UUID sub) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", sub.toString())
                .build();
    }

    @Test
    void createGetCancel_full_flow() throws Exception {
        // Create
        MvcResult createResult = mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
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

        // Get — amount must match create (B9 regression)
        String getAmount = objectMapper.readTree(
                mockMvc.perform(get("/v1/payments/{id}", paymentId)
                                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(paymentId))
                        .andExpect(jsonPath("$.status").value("CREATED"))
                        .andReturn().getResponse().getContentAsString()
        ).get("amount").asText();
        assertThat(getAmount).isEqualTo("100.00");

        // Cancel
        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Cancel again should return 409
        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_STATE"));
    }

    @Test
    void create_then_replay_returns_201_same_payment_id() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = "{\"amount\":\"25.00\",\"currency\":\"EUR\"}";

        MvcResult first = mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        MvcResult replay = mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
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
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"999.00\",\"currency\":\"EUR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_IDEMPOTENCY_KEY"));
    }

    @Test
    void get_other_owners_payment_returns_404() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10.00\",\"currency\":\"GBP\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        UUID otherId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        Jwt otherJwt = Jwt.withTokenValue("token2")
                .header("alg", "RS256")
                .claim("sub", otherId.toString())
                .build();

        mockMvc.perform(get("/v1/payments/{id}", paymentId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(otherJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void same_key_different_owner_does_not_replay_cross_user() throws Exception {
        String sharedKey = UUID.randomUUID().toString();
        String body = "{\"amount\":\"1.00\",\"currency\":\"USD\"}";
        UUID otherOwner = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

        MvcResult first = mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String firstOwnerId = objectMapper.readTree(first.getResponse().getContentAsString()).get("ownerId").asText();

        // Other user using same idempotency key gets their own payment, not user A's
        MvcResult second = mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(otherOwner)))
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
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void zero_amount_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"0\",\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missing_idempotency_key_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformed_json_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void non_uuid_path_returns_400() throws Exception {
        mockMvc.perform(get("/v1/payments/not-a-uuid")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt(OWNER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
