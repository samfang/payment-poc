package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Currency;

/** Converts payments.amount from NUMERIC(19,4) to BIGINT minor units. Fails loudly on inexact rows. */
public class V2__AmountToMinorUnits extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE payments ADD COLUMN amount_minor_units BIGINT");
        }

        try (Statement sel = conn.createStatement();
             ResultSet rs = sel.executeQuery("SELECT id, amount, currency_code FROM payments")) {
            while (rs.next()) {
                String id = rs.getString("id");
                BigDecimal amount = rs.getBigDecimal("amount");
                String currencyCode = rs.getString("currency_code");

                Currency currency = Currency.getInstance(currencyCode);
                int exponent = currency.getDefaultFractionDigits();
                BigDecimal multiplier = BigDecimal.TEN.pow(exponent);
                BigDecimal minorUnits = amount.multiply(multiplier);

                if (minorUnits.stripTrailingZeros().scale() > 0) {
                    throw new RuntimeException(
                            "Cannot convert amount for payment " + id + ": " + amount +
                            " " + currencyCode + " does not convert exactly to minor units");
                }

                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE payments SET amount_minor_units = ? WHERE id = ?::uuid")) {
                    upd.setLong(1, minorUnits.longValueExact());
                    upd.setString(2, id);
                    upd.executeUpdate();
                }
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE payments DROP CONSTRAINT chk_payments_amount_positive");
            stmt.execute("ALTER TABLE payments DROP COLUMN amount");
            stmt.execute("ALTER TABLE payments RENAME COLUMN amount_minor_units TO amount");
            stmt.execute("ALTER TABLE payments ALTER COLUMN amount SET NOT NULL");
            stmt.execute("ALTER TABLE payments ADD CONSTRAINT chk_payments_amount_positive CHECK (amount > 0)");
        }
    }
}
