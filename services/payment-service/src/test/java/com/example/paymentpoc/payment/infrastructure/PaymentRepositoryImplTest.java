package com.example.paymentpoc.payment.infrastructure;

import com.example.paymentpoc.payment.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PaymentRepositoryImpl.class)
class PaymentRepositoryImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentRepositoryImpl repository;

    private static final UUID OWNER = UUID.randomUUID();
    private static final Money MONEY = new Money(new BigDecimal("25.00"), "EUR");

    @Test
    void should_persist_and_retrieve_all_fields() {
        Payment saved = repository.save(Payment.create(OWNER, MONEY));

        Payment found = repository.findById(saved.id()).orElseThrow();
        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.ownerId()).isEqualTo(OWNER);
        assertThat(found.money().amount()).isEqualByComparingTo("25.00");
        assertThat(found.money().currencyCode()).isEqualTo("EUR");
        assertThat(found.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(found.createdAt()).isNotNull();
        assertThat(found.updatedAt()).isNotNull();
    }

    @Test
    void should_return_empty_for_unknown_id() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void should_persist_status_change_on_save() {
        Payment payment = Payment.create(OWNER, MONEY);
        payment.cancel();
        Payment saved = repository.save(payment);

        assertThat(repository.findById(saved.id()).orElseThrow().status())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void should_return_first_page_in_created_at_order() {
        Payment p1 = repository.save(Payment.create(OWNER, MONEY));
        Payment p2 = repository.save(Payment.create(OWNER, MONEY));

        List<Payment> page = repository.findByOwner(OWNER, null, null, 10);
        assertThat(page).hasSize(2);
        // Stable ordering: earlier createdAt first (or same instant, id ordering)
        assertThat(page.stream().map(Payment::id).toList())
                .containsExactlyInAnyOrder(p1.id(), p2.id());
    }

    @Test
    void should_exclude_cursor_position_in_next_page() {
        Payment p1 = repository.save(Payment.create(OWNER, MONEY));
        Payment p2 = repository.save(Payment.create(OWNER, MONEY));

        // First page of size 1 returns p1
        List<Payment> firstPage = repository.findByOwner(OWNER, null, null, 1);
        assertThat(firstPage).hasSize(1);
        Payment last = firstPage.get(0);

        // Second page should return only the other payment
        List<Payment> secondPage = repository.findByOwner(OWNER, last.createdAt(), last.id(), 1);
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).id()).isNotEqualTo(last.id());
    }

    @Test
    void should_not_return_payments_belonging_to_other_owners() {
        UUID otherOwner = UUID.randomUUID();
        repository.save(Payment.create(otherOwner, MONEY));
        repository.save(Payment.create(OWNER, MONEY));

        List<Payment> page = repository.findByOwner(OWNER, null, null, 10);
        assertThat(page).allMatch(p -> p.ownerId().equals(OWNER));
    }
}
