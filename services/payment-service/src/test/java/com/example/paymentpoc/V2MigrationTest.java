package com.example.paymentpoc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/**
 * Verifies that V2__AmountToMinorUnits correctly converts NUMERIC(19,4) rows
 * to BIGINT minor units, and fails loudly when a row cannot convert exactly.
 */
@Testcontainers
class V2MigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway flyway(String target) {
        var builder = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            builder = builder.target(target);
        }
        return builder.load();
    }

    @BeforeEach
    void clean() {
        flyway(null).clean();
    }

    @Test
    void v2_converts_all_currency_exponents_correctly() throws Exception {
        flyway("1").migrate();

        UUID usdId = UUID.randomUUID();
        UUID jpyId = UUID.randomUUID();
        UUID kwdId = UUID.randomUUID();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            insertPayment(conn, usdId, "5.0000", "USD");
            insertPayment(conn, jpyId, "500.0000", "JPY");
            insertPayment(conn, kwdId, "10.5000", "KWD");
        }

        flyway(null).migrate();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertThat(queryAmount(conn, usdId)).isEqualTo(500L);
            assertThat(queryAmount(conn, jpyId)).isEqualTo(500L);
            assertThat(queryAmount(conn, kwdId)).isEqualTo(10500L);
        }
    }

    @Test
    void v2_fails_loudly_on_inexact_row() throws Exception {
        flyway("1").migrate();

        UUID badId = UUID.randomUUID();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            // 5.0050 USD × 100 = 500.50 — not an integer, migration must fail naming this row
            insertPayment(conn, badId, "5.0050", "USD");
        }

        assertThatThrownBy(() -> flyway(null).migrate())
                .satisfies(ex -> {
                    Throwable t = ex;
                    boolean found = false;
                    while (t != null) {
                        if (t.getMessage() != null && t.getMessage().contains(badId.toString())) {
                            found = true;
                            break;
                        }
                        t = t.getCause();
                    }
                    assertThat(found).as("exception chain must name the bad row id").isTrue();
                });
    }

    private void insertPayment(Connection conn, UUID id, String amount, String currencyCode)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO payments(id, owner_id, amount, currency_code, status, created_at, updated_at)" +
                " VALUES (?::uuid, ?::uuid, ?, ?, 'CREATED', NOW(), NOW())")) {
            ps.setString(1, id.toString());
            ps.setString(2, UUID.randomUUID().toString());
            ps.setBigDecimal(3, new java.math.BigDecimal(amount));
            ps.setString(4, currencyCode);
            ps.executeUpdate();
        }
    }

    private long queryAmount(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT amount FROM payments WHERE id = ?::uuid")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }
}
