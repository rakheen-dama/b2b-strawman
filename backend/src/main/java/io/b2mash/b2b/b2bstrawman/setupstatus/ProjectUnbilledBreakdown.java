package io.b2mash.b2b.b2bstrawman.setupstatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProjectUnbilledBreakdown(
    UUID projectId, String projectName, BigDecimal hours, BigDecimal amount, int entryCount) {}
