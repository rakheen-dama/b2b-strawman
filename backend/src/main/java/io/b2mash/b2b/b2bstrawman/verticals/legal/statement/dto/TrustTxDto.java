package io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trust transaction projection (deposit / payment / disbursement-payment) for the Statement of
 * Account (architecture §67.6.1).
 *
 * <p>Field names {@code transactionDate} and {@code transactionType} match the SoA Tiptap
 * template's loop-table column keys verbatim ({@code
 * template-packs/legal-za/statement-of-account.json}). The renderer copies row values via {@code
 * row.get(colKey)} after Jackson serialisation, so any divergence between record-component names
 * and the template's {@code "key"} attribute renders the column as blank — the cause of
 * GAP-OBS-Day60-SoA-Fees/Trust-Empty in cycle 1.
 *
 * <p>{@code transactionTypeLabel} is the client-facing label the Payments table's Type column binds
 * (G89 W2-c3, legal-za v15); {@code transactionType} keeps the stored enum for any template that
 * still binds it.
 */
public record TrustTxDto(
    UUID id,
    LocalDate transactionDate,
    String transactionType,
    String transactionTypeLabel,
    BigDecimal amount,
    String reference,
    String description) {}
