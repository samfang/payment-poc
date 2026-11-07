package com.example.paymentpoc.payment.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void should_create_money_when_amount_is_positive() {
        Money money = new Money(new BigDecimal("19.99"), "USD");
        assertThat(money.amount()).isEqualByComparingTo("19.99");
        assertThat(money.currencyCode()).isEqualTo("USD");
    }

    @Test
    void should_reject_zero_amount() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void should_reject_negative_amount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void should_reject_blank_currency_code() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_currency_code_longer_than_3_chars() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), "USDX"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
