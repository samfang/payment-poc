package com.example.paymentpoc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression probe from Charles_Manager's review. All items must stay green. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReviewProbeTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @TestConfiguration
    static class C {
        @Bean
        JwtDecoder d() {
            return t -> { throw new UnsupportedOperationException(); };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    /** Builder with both read and write scopes (probe tests all endpoint types). */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String sub) {
        return b.with(jwt().jwt(j -> j.subject(sub))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_payments:read"),
                        new SimpleGrantedAuthority("SCOPE_payments:write")));
    }

    private int create(String sub, String key, String body) throws Exception {
        var b = as(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body), sub);
        if (key != null) b = b.header("Idempotency-Key", key);
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    private String createAndGetBody(String sub, String key, String body) throws Exception {
        var b = as(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body), sub);
        if (key != null) b = b.header("Idempotency-Key", key);
        return mvc.perform(b).andReturn().getResponse().getContentAsString();
    }

    @Test
    void probes() throws Exception {
        String userA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();

        // B1: zero amount → 400
        assertThat(create(userA, UUID.randomUUID().toString(),
                "{\"amount\":0,\"currency\":\"USD\"}")).isEqualTo(400);

        // B2: missing Idempotency-Key → 400
        assertThat(create(userA, null, "{\"amount\":100,\"currency\":\"USD\"}")).isEqualTo(400);

        // B3: malformed JSON → 400
        assertThat(create(userA, UUID.randomUUID().toString(), "{bad")).isEqualTo(400);

        // B6: same key, different body → 409
        String key = UUID.randomUUID().toString();
        assertThat(create(userA, key, "{\"amount\":500,\"currency\":\"USD\"}")).isEqualTo(201);
        assertThat(create(userA, key, "{\"amount\":99900,\"currency\":\"EUR\"}")).isEqualTo(409);

        // B7: same key, other user does NOT get user A's payment
        String firstBody = createAndGetBody(userA, key + "-scope",
                "{\"amount\":500,\"currency\":\"USD\"}");
        String otherBody = createAndGetBody(userB, key + "-scope",
                "{\"amount\":100,\"currency\":\"USD\"}");
        String firstId = om.readTree(firstBody).get("id").asText();
        String otherOwnerId = om.readTree(otherBody).get("ownerId").asText();
        assertThat(om.readTree(otherBody).get("id").asText()).isNotEqualTo(firstId);
        assertThat(otherOwnerId).isEqualTo(userB);

        // B9: amount representation consistent between create and get
        String createBody = createAndGetBody(userA, UUID.randomUUID().toString(),
                "{\"amount\":500,\"currency\":\"USD\"}");
        String paymentId = om.readTree(createBody).get("id").asText();
        long amountOnCreate = om.readTree(createBody).get("amount").asLong();
        String getBody = mvc.perform(as(get("/v1/payments/{id}", paymentId), userA))
                .andReturn().getResponse().getContentAsString();
        long amountOnGet = om.readTree(getBody).get("amount").asLong();
        assertThat(amountOnGet).isEqualTo(amountOnCreate);

        // B4: invalid path UUID → 400
        assertThat(mvc.perform(as(get("/v1/payments/not-a-uuid"), userA))
                .andReturn().getResponse().getStatus()).isEqualTo(400);

        // B5: invalid cursor → 400
        assertThat(mvc.perform(as(get("/v1/payments?cursor=@@@"), userA))
                .andReturn().getResponse().getStatus()).isEqualTo(400);

        // B8: pagination no duplicates across pages (30 payments, pageSize=7)
        String userC = UUID.randomUUID().toString();
        for (int i = 0; i < 30; i++) {
            create(userC, UUID.randomUUID().toString(), "{\"amount\":100,\"currency\":\"USD\"}");
        }
        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String url = "/v1/payments?pageSize=7" + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode n = om.readTree(mvc.perform(as(get(url), userC))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            n.get("items").forEach(it -> seen.add(it.get("id").asText()));
            cursor = n.hasNonNull("nextCursor") ? n.get("nextCursor").asText() : null;
        } while (cursor != null && ++pages < 20);
        assertThat(seen).as("total items seen").hasSize(30);
        assertThat(new HashSet<>(seen)).as("unique items (no duplicates)").hasSize(30);
    }
}
