package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.payment.application.*;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that each endpoint enforces the required OAuth2 scope.
 * A valid JWT with no scope (or the wrong scope) must receive 403 Forbidden.
 * These tests fail until @PreAuthorize annotations are added to PaymentController.
 */
@WebMvcTest(PaymentController.class)
@Import({IdempotencyStore.class, SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class PaymentControllerScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PAYMENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    // ── POST /v1/payments — requires payments:write ────────────────────────────

    @Test
    void createPayment_no_scope_returns_403() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString())))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPayment_read_scope_only_returns_403() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:read")))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isForbidden());
    }

    // ── GET /v1/payments/{id} — requires payments:read ─────────────────────────

    @Test
    void getPayment_no_scope_returns_403() throws Exception {
        mockMvc.perform(get("/v1/payments/{id}", PAYMENT_ID)
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPayment_write_scope_only_returns_403() throws Exception {
        mockMvc.perform(get("/v1/payments/{id}", PAYMENT_ID)
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPayment_admin_scope_only_returns_403() throws Exception {
        mockMvc.perform(get("/v1/payments/{id}", PAYMENT_ID)
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:admin"))))
                .andExpect(status().isForbidden());
    }

    // ── GET /v1/payments — requires payments:read ──────────────────────────────

    @Test
    void listPayments_no_scope_returns_403() throws Exception {
        mockMvc.perform(get("/v1/payments")
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPayments_admin_scope_only_returns_403() throws Exception {
        mockMvc.perform(get("/v1/payments")
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:admin"))))
                .andExpect(status().isForbidden());
    }

    // ── POST /v1/payments/{id}/cancel — requires payments:write ───────────────

    @Test
    void cancelPayment_no_scope_returns_403() throws Exception {
        mockMvc.perform(post("/v1/payments/{id}/cancel", PAYMENT_ID)
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelPayment_read_scope_only_returns_403() throws Exception {
        mockMvc.perform(post("/v1/payments/{id}/cancel", PAYMENT_ID)
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:read"))))
                .andExpect(status().isForbidden());
    }
}
