package io.b2mash.b2b.b2bstrawman.invoice.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UnbilledTimeResponse(
    UUID customerId,
    String customerName,
    List<UnbilledProjectGroup> projects,
    Map<String, CurrencyTotal> grandTotals) {}
