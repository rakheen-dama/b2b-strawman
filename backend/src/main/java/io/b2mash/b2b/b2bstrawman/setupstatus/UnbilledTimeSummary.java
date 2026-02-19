package io.b2mash.b2b.b2bstrawman.setupstatus;

import java.math.BigDecimal;
import java.util.List;

public record UnbilledTimeSummary(
    BigDecimal totalHours,
    BigDecimal totalAmount,
    String currency,
    int entryCount,
    List<ProjectUnbilledBreakdown> byProject) {}
