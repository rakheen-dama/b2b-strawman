package io.b2mash.b2b.b2bstrawman.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.UnbilledDisbursementDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unbilled invoice-picker read model for a customer. Contains three parallel tracks: time entries
 * (grouped by project), expenses, and — for tenants with the {@code disbursements} module enabled
 * (legal vertical) — legal disbursements. Non-legal tenants see an empty {@code disbursements}
 * list, keeping the response byte-compatible.
 */
public record UnbilledTimeResponse(
    UUID customerId,
    String customerName,
    List<UnbilledProjectGroup> projects,
    Map<String, CurrencyTotal> grandTotals,
    List<UnbilledExpenseEntry> unbilledExpenses,
    Map<String, BigDecimal> unbilledExpenseTotals,
    @JsonInclude(JsonInclude.Include.ALWAYS) List<UnbilledDisbursementDto> disbursements) {}
