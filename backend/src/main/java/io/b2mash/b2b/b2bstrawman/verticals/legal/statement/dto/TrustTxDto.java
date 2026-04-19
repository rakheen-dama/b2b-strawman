package io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trust transaction projection (deposit / payment / disbursement-payment) for the Statement of
 * Account (architecture §67.6.1).
 */
public record TrustTxDto(
    UUID id,
    LocalDate date,
    String type,
    BigDecimal amount,
    String reference,
    String description) {}
