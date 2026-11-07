package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.payment.application.PaymentService;
import com.example.paymentpoc.shared.config.ApiAccessDeniedHandler;
import com.example.paymentpoc.shared.config.ApiAuthenticationEntryPoint;
import com.example.paymentpoc.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that unauthenticated (401) and unauthorized (403) responses use
 * the shared error envelope rather than Spring Security's default HTML/JSON.
 * These tests fail until custom AuthenticationEntryPoint and AccessDeniedHandler
 * are wired into SecurityConfig.
 */
@WebMvcTest(PaymentController.class)
@Import({IdempotencyStore.class, SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class PaymentControllerAuthErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ── 401 Unauthenticated — shared error envelope ───────────────────────────

    @Test
    void no_token_returns_401_with_shared_error_envelope() throws Exception {
        mockMvc.perform(get("/v1/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.debugId").isString());
    }

    @Test
    void no_token_on_post_returns_401_with_shared_error_envelope() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"currency\":\"USD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.error.debugId").isString());
    }

    // ── 403 Forbidden — shared error envelope ─────────────────────────────────

    @Test
    void wrong_scope_returns_403_with_shared_error_envelope() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:read")))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"currency\":\"USD\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.debugId").isString());
    }

    @Test
    void no_scope_on_get_returns_403_with_shared_error_envelope() throws Exception {
        mockMvc.perform(get("/v1/payments/{id}", UUID.randomUUID())
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.debugId").isString());
    }
}
