package io.b2mash.b2b.b2bstrawman.portal.directory;

import java.time.Instant;

/**
 * One (email, firm) routing row in {@code portal.portal_contact_directory} (Phase 88 U11, ADR-351).
 * Presence only: never names, customer/contact ids, tokens or tenant data (§13.6.3). Read by {@code
 * JdbcClient} (DataClassRowMapper maps by column name) like the {@code Portal*View} records.
 */
public record PortalContactDirectoryEntry(
    String emailLower, String orgId, String tenantSchema, String orgName, Instant updatedAt) {}
