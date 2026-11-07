package com.example.paymentpoc.payment.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void usd_2_decimal_exponent_stores_minor_units() {
        Money money = new Money(1000L, "USD");
        assertThat(money.minorUnits()).isEqualTo(1000L);
        assertThat(money.currencyCode()).isEqualTo("USD");
        assertThat(money.currencyExponent()).isEqualTo(2);
    }

    @Test
    void jpy_zero_decimal_exponent_stores_minor_units() {
        Money money = new Money(500L, "JPY");
        assertThat(money.minorUnits()).isEqualTo(500L);
        assertThat(money.currencyExponent()).isEqualTo(0);
    }

    @Test
    void kwd_three_decimal_exponent_stores_minor_units() {
        Money money = new Money(10500L, "KWD");
        assertThat(money.minorUnits()).isEqualTo(10500L);
        assertThat(money.currencyExponent()).isEqualTo(3);
    }

    @Test
    void max_minor_units_accepted() {
        assertThatCode(() -> new Money(99_999_999L, "USD")).doesNotThrowAnyException();
    }

    @Test
    void amount_exceeding_max_rejected() {
        assertThatThrownBy(() -> new Money(100_000_000L, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zero_amount_rejected() {
        assertThatThrownBy(() -> new Money(0L, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negative_amount_rejected() {
        assertThatThrownBy(() -> new Money(-1L, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void xau_precious_metal_no_minor_unit_rejected() {
        assertThatThrownBy(() -> new Money(100L, "XAU"))
                .isInstanceOf(InvalidCurrencyException.class);
    }

    @Test
    void unknown_currency_code_rejected() {
        assertThatThrownBy(() -> new Money(100L, "ZZZ"))
                .isInstanceOf(InvalidCurrencyException.class);
    }

    @Test
    void blank_currency_code_rejected() {
        assertThatThrownBy(() -> new Money(100L, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void currency_code_wrong_length_rejected() {
        assertThatThrownBy(() -> new Money(100L, "US"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
