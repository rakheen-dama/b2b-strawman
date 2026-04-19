package io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fee line projection of a billable {@code TimeEntry} for the Statement of Account (architecture
 * §67.6.1). One row per (date, member, narrative) triple already aggregated by the context builder.
 */
public record FeeLineDto(
    UUID id,
    LocalDate date,
    UUID memberId,
    String memberName,
    String description,
    BigDecimal hours,
    BigDecimal rate,
    BigDecimal amount) {}
