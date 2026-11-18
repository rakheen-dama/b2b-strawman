package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import java.math.BigDecimal;
import java.util.List;

/**
 * One integrity flag (§16.3.9). {@code amount} is a positive magnitude, or null where no single
 * figure applies; {@code message} is a sentence for the firm (never a raw code). For a blocking
 * flag the message is the completion gate's sentence(s), so the card and the refusal say the same
 * thing. Top-level (not nested in {@link TrustIntegrityService}) because the reconciliation entity
 * stores a list of these in its {@code integrity_flags} column.
 */
public record IntegrityFlag(
    String code, String severity, String message, BigDecimal amount, List<FlagSubject> subjects) {}
