package io.b2mash.b2b.b2bstrawman.datarequest;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Preview of what will be affected by anonymizing a customer. Returned by {@link
 * DataAnonymizationService#previewAnonymization(UUID)} to allow users to review the impact before
 * confirming.
 */
public record AnonymizationPreview(
    UUID customerId,
    String customerName,
    int portalContacts,
    int projects,
    int documents,
    int timeEntries,
    int invoices,
    int comments,
    int customFieldValues,
    int financialRecordsRetained,
    LocalDate financialRetentionExpiresAt) {}
