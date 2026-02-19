package io.b2mash.b2b.b2bstrawman.setupstatus;

import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;

public record UnbilledTimeSummary(
    BigDecimal totalHours,
    BigDecimal totalAmount,
    String currency,
    int entryCount,
    @Nullable List<ProjectUnbilledBreakdown> byProject) {}
