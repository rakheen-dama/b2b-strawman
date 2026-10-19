package io.b2mash.b2b.b2bstrawman.setupstatus;

import java.math.BigDecimal;
import java.util.UUID;

public record MatterUnbilledBreakdown(
    UUID matterId, String matterName, BigDecimal hours, BigDecimal amount, int entryCount) {}
