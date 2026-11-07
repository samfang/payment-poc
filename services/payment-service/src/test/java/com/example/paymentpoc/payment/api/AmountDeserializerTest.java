package com.example.paymentpoc.payment.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Verifies that AmountDeserializer rejects any non-integer JSON token. */
class AmountDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Long deserialize(String json) throws Exception {
        return mapper.readValue("{\"amount\":" + json + "}", AmountWrapper.class).amount();
    }

    record AmountWrapper(
            @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = AmountDeserializer.class)
            Long amount) {}

    @Test
    void integer_token_accepted() throws Exception {
        assertThat(deserialize("1000")).isEqualTo(1000L);
    }

    @Test
    void large_integer_accepted() throws Exception {
        assertThat(deserialize("99999999")).isEqualTo(99_999_999L);
    }

    @Test
    void float_with_zero_fractional_part_rejected() {
        assertThatThrownBy(() -> deserialize("500.0"))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void float_with_nonzero_fractional_part_rejected() {
        assertThatThrownBy(() -> deserialize("10.99"))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void string_token_rejected() {
        assertThatThrownBy(() -> deserialize("\"1000\""))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void null_token_returns_null() throws Exception {
        assertThat(deserialize("null")).isNull();
    }
}
