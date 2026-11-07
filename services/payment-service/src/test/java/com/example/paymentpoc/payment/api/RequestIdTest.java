package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.payment.application.*;
import com.example.paymentpoc.shared.config.ApiAccessDeniedHandler;
import com.example.paymentpoc.shared.config.ApiAuthenticationEntryPoint;
import com.example.paymentpoc.shared.config.RequestIdFilter;
import com.example.paymentpoc.shared.config.SecurityConfig;
import com.example.paymentpoc.shared.exception.ResourceNotFoundException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies API_GUIDELINES §13.1: every response has a Request-Id header equal to error.debugId on errors.
 * Tests are committed before RequestIdFilter is implemented (TDD).
 */
@WebMvcTest(PaymentController.class)
@Import({IdempotencyStore.class, SecurityConfig.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, RequestIdFilter.class})
class RequestIdTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PAYMENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private PaymentResult sampleResult() {
        return new PaymentResult(PAYMENT_ID, OWNER_ID, "10.00", "USD", "CREATED",
                Instant.now(), Instant.now());
    }

    @Test
    void created_201_includes_request_id_header() throws Exception {
        when(paymentService.create(any())).thenReturn(sampleResult());

        mockMvc.perform(post("/v1/payments")
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10.00\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Request-Id"));
    }

    @Test
    void ok_200_includes_request_id_header() throws Exception {
        when(paymentService.get(eq(PAYMENT_ID), any(), anyBoolean())).thenReturn(sampleResult());

        mockMvc.perform(get("/v1/payments/{id}", PAYMENT_ID)
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:read"))))
                .andExpect(status().isOk())
                .andExpect(header().exists("Request-Id"));
    }

    @Test
    void bad_request_400_request_id_equals_debug_id() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/payments/not-a-uuid")
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:read"))))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("Request-Id"))
                .andReturn();

        assertRequestIdMatchesDebugId(result);
    }

    @Test
    void not_found_404_request_id_equals_debug_id() throws Exception {
        when(paymentService.get(eq(PAYMENT_ID), any(), anyBoolean()))
                .thenThrow(new ResourceNotFoundException("Payment", PAYMENT_ID.toString()));

        MvcResult result = mockMvc.perform(get("/v1/payments/{id}", PAYMENT_ID)
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:read"))))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("Request-Id"))
                .andReturn();

        assertRequestIdMatchesDebugId(result);
    }

    @Test
    void unauthorized_401_request_id_equals_debug_id() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/payments/{id}", PAYMENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Request-Id"))
                .andReturn();

        assertRequestIdMatchesDebugId(result);
    }

    @Test
    void forbidden_403_request_id_equals_debug_id() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/payments/{id}", PAYMENT_ID)
                        .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_payments:write"))))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("Request-Id"))
                .andReturn();

        assertRequestIdMatchesDebugId(result);
    }

    private static void assertRequestIdMatchesDebugId(MvcResult result) throws Exception {
        String requestId = result.getResponse().getHeader("Request-Id");
        String debugId = JsonPath.read(result.getResponse().getContentAsString(), "$.error.debugId");
        assertThat(requestId).isNotBlank();
        assertThat(debugId).isEqualTo(requestId);
    }
}
