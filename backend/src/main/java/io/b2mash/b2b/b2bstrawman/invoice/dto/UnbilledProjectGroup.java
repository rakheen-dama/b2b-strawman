package io.b2mash.b2b.b2bstrawman.invoice.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UnbilledProjectGroup(
    UUID projectId,
    String projectName,
    List<UnbilledTimeEntry> entries,
    Map<String, CurrencyTotal> totals) {}
