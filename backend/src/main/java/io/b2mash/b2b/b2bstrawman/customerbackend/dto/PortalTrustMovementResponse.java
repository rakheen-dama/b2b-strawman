package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Row shape for {@code GET /portal/trust/movements?limit=N}.
 *
 * <p>Cross-matter feed of the most recent trust transactions for the authenticated portal contact's
 * customer. Drives the "Last trust movement" tile on the portal home page (and any future activity
 * feed). Distinct from {@link PortalTrustTransactionResponse} because the home tile is
 * matter-agnostic and surfaces a {@code matterName} hint, whereas the per-matter list already has
 * the matter context in the URL.
 *
 * <p>{@code currency} reflects the firm's display currency for the trust ledger. The portal
 * read-model does not store currency per row (ADR-253) — the service-layer mapping defaults to the
 * legal-za vertical default ({@code "ZAR"}) since trust accounting is currently only enabled on
 * that vertical.
 *
 * <p>{@code transactionDate} is the transaction's SA business date — the date the tile shows
 * (G89-F39); {@code occurredAt} is the approval / recorded instant.
 */
public record PortalTrustMovementResponse(
    UUID id,
    String type,
    BigDecimal amount,
    String currency,
    LocalDate transactionDate,
    Instant occurredAt,
    String matterName,
    String description) {}
