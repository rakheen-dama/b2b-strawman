package io.b2mash.b2b.b2bstrawman.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.UnbilledDisbursementDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unbilled invoice-picker read model for a client. Contains three parallel tracks: time entries
 * (grouped by matter), expenses, and — for tenants with the {@code disbursements} module enabled
 * (legal vertical) — legal disbursements. Non-legal tenants see an empty {@code disbursements}
 * list, keeping the response byte-compatible.
 */
public record UnbilledTimeResponse(
    UUID clientId,
    String clientName,
    List<UnbilledMatterGroup> matters,
    Map<String, CurrencyTotal> grandTotals,
    List<UnbilledExpenseEntry> unbilledExpenses,
    Map<String, BigDecimal> unbilledExpenseTotals,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<UnbilledDisbursementDto> disbursements) {}
