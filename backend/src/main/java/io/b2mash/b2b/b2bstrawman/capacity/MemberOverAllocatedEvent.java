package io.b2mash.b2b.b2bstrawman.capacity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MemberOverAllocatedEvent(
    UUID memberId,
    LocalDate weekStart,
    BigDecimal totalAllocated,
    BigDecimal effectiveCapacity,
    BigDecimal overageHours) {}
