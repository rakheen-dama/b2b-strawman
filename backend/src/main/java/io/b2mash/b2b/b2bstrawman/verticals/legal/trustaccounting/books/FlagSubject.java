package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What an {@link IntegrityFlag} is about: {@code kind} is one of the {@code
 * TrustIntegrityService.SUBJECT_*} constants; {@code amount} is a positive magnitude or null.
 */
public record FlagSubject(String kind, UUID id, String name, BigDecimal amount) {}
