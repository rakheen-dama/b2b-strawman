package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * Request to update the firm-wide default expense markup percentage.
 *
 * <p>Bounds mirror the time-tracking settings form (non-negative, ceiling of 999) and the {@code
 * NUMERIC(5,2)} column on {@code org_settings.default_expense_markup_percent}.
 *
 * <p><strong>Null semantics:</strong> unlike the multi-field sibling PATCH endpoints (e.g.
 * time-reminders) where a {@code null} field means "keep existing", this single-field endpoint
 * treats an explicit {@code null} as a request to CLEAR the markup. The form allows the operator to
 * empty the markup input, which must propagate as a clear, not a no-op.
 */
public record UpdateExpenseSettingsRequest(
    @DecimalMin(value = "0.00", message = "defaultExpenseMarkupPercent must be non-negative")
        @DecimalMax(
            value = "999.99",
            message = "defaultExpenseMarkupPercent must not exceed 999.99")
        BigDecimal defaultExpenseMarkupPercent) {}
