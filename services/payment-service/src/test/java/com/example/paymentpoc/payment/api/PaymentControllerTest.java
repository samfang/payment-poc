package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.shared.config.ApiAccessDeniedHandler;
import com.example.paymentpoc.shared.config.ApiAuthenticationEntryPoint;
import com.example.paymentpoc.shared.config.SecurityConfig;

import com.example.paymentpoc.payment.application.*;
import com.example.paymentpoc.payment.domain.InvalidPaymentStateException;
import com.example.paymentpoc.payment.domain.PaymentStatus;
import com.example.paymentpoc.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import({IdempotencyStore.class, SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PAYMENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    /** JWT with payments:read scope. */
    private static RequestPostProcessor readJwt(UUID sub) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(sub.toString()))
                .authorities(new SimpleGrantedAuthority("SCOPE_payments:read"));
    }

    /** JWT with payments:write scope. */
    private static RequestPostProcessor writeJwt(UUID sub) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(sub.toString()))
                .authorities(new SimpleGrantedAuthority("SCOPE_payments:write"));
    }

    /** JWT with both scopes (for tests that chain read+write). */
    private static RequestPostProcessor readWriteJwt(UUID sub) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(sub.toString()))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_payments:read"),
                        new SimpleGrantedAuthority("SCOPE_payments:write"));
    }

    private PaymentResult createdResult() {
        return new PaymentResult(PAYMENT_ID, OWNER_ID, 5000L, "USD",
                "CREATED", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private PaymentResult cancelledResult() {
        return new PaymentResult(PAYMENT_ID, OWNER_ID, 5000L, "USD",
                "CANCELLED", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:01Z"));
    }

    // ── POST /v1/payments ──────────────────────────────────────────────────────

    @Test
    void createPayment_returns_201_with_location_and_body() throws Exception {
        when(paymentService.create(any())).thenReturn(createdResult());

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000,\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/payments/" + PAYMENT_ID))
                .andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.links[?(@.rel=='self')]").exists())
                .andExpect(jsonPath("$.links[?(@.rel=='cancel')]").exists());
    }

    @Test
    void createPayment_replay_returns_original_201_without_calling_service_again() throws Exception {
        when(paymentService.create(any())).thenReturn(createdResult());
        String key = UUID.randomUUID().toString();
        String body = "{\"amount\":5000,\"currency\":\"USD\"}";

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(paymentService, times(1)).create(any());
    }

    @Test
    void createPayment_same_key_different_body_returns_409() throws Exception {
        when(paymentService.create(any())).thenReturn(createdResult());
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000,\"currency\":\"USD\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":99900,\"currency\":\"EUR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_IDEMPOTENCY_KEY"));
    }

    @Test
    void createPayment_same_key_different_owner_does_not_replay() throws Exception {
        when(paymentService.create(any())).thenReturn(createdResult());
        String key = UUID.randomUUID().toString();
        String body = "{\"amount\":5000,\"currency\":\"USD\"}";
        UUID otherOwner = UUID.randomUUID();

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(otherOwner))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(paymentService, times(2)).create(any());
    }

    @Test
    void createPayment_zero_amount_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_negative_amount_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_float_amount_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.0,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_string_amount_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"500\",\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_amount_too_large_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000000,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_missing_idempotency_key_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_missing_currency_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_malformed_json_returns_400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(writeJwt(OWNER_ID))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000,\"currency\":\"USD\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /v1/payments/{id} ──────────────────────────────────────────────────

    @Test
    void getPayment_returns_200_with_body() throws Exception {
        when(paymentService.get(PAYMENT_ID, OWNER_ID, false)).thenReturn(createdResult());

        mockMvc.perform(get("/v1/payments/{id}", PAYMENT_ID)
                        .with(readJwt(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getPayment_non_uuid_path_returns_400() throws Exception {
        mockMvc.perform(get("/v1/payments/not-a-uuid")
                        .with(readJwt(OWNER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getPayment_unknown_returns_404() throws Exception {
        when(paymentService.get(eq(PAYMENT_ID), any(), anyBoolean()))
                .thenThrow(new ResourceNotFoundException("Payment", PAYMENT_ID.toString()));

        mockMvc.perform(get("/v1/payments/{id}", PAYMENT_ID)
                        .with(readJwt(OWNER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // ── GET /v1/payments ───────────────────────────────────────────────────────

    @Test
    void listPayments_returns_200_with_items_and_self_link() throws Exception {
        PaymentListResult listResult = new PaymentListResult(List.of(createdResult()), null);
        when(paymentService.list(eq(OWNER_ID), eq(false), isNull(), eq(20))).thenReturn(listResult);

        mockMvc.perform(get("/v1/payments")
                        .with(readJwt(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.links[?(@.rel=='self')]").exists())
                .andExpect(jsonPath("$.links[?(@.rel=='self')].href").value(org.hamcrest.Matchers.contains("/v1/payments?pageSize=20")));
    }

    @Test
    void listPayments_with_more_rows_includes_next_link() throws Exception {
        PaymentListResult listResult = new PaymentListResult(List.of(createdResult()), "nextCursorToken");
        when(paymentService.list(eq(OWNER_ID), eq(false), isNull(), eq(20))).thenReturn(listResult);

        mockMvc.perform(get("/v1/payments")
                        .with(readJwt(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").value("nextCursorToken"))
                .andExpect(jsonPath("$.links[?(@.rel=='next')]").exists());
    }

    @Test
    void listPayments_invalid_cursor_returns_400() throws Exception {
        when(paymentService.list(any(), anyBoolean(), eq("@@@"), anyInt()))
                .thenThrow(new com.example.paymentpoc.shared.exception.InvalidCursorException("@@@"));

        mockMvc.perform(get("/v1/payments?cursor=@@@")
                        .with(readJwt(OWNER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listPayments_page_size_out_of_range_returns_400() throws Exception {
        mockMvc.perform(get("/v1/payments?pageSize=0")
                        .with(readJwt(OWNER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── POST /v1/payments/{id}/cancel ──────────────────────────────────────────

    @Test
    void cancelPayment_CREATED_returns_200_with_CANCELLED_status() throws Exception {
        when(paymentService.cancel(PAYMENT_ID, OWNER_ID, false)).thenReturn(cancelledResult());

        mockMvc.perform(post("/v1/payments/{id}/cancel", PAYMENT_ID)
                        .with(writeJwt(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelPayment_COMPLETED_returns_409_with_INVALID_PAYMENT_STATE() throws Exception {
        when(paymentService.cancel(eq(PAYMENT_ID), any(), anyBoolean()))
                .thenThrow(new InvalidPaymentStateException(PaymentStatus.COMPLETED));

        mockMvc.perform(post("/v1/payments/{id}/cancel", PAYMENT_ID)
                        .with(writeJwt(OWNER_ID)))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Error-Code", "INVALID_PAYMENT_STATE"))
                .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_STATE"));
    }

    @Test
    void cancelPayment_CANCELLED_returns_409_with_INVALID_PAYMENT_STATE() throws Exception {
        when(paymentService.cancel(eq(PAYMENT_ID), any(), anyBoolean()))
                .thenThrow(new InvalidPaymentStateException(PaymentStatus.CANCELLED));

        mockMvc.perform(post("/v1/payments/{id}/cancel", PAYMENT_ID)
                        .with(writeJwt(OWNER_ID)))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Error-Code", "INVALID_PAYMENT_STATE"))
                .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_STATE"));
    }
}
