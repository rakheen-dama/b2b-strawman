package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.time.Instant;
import java.util.UUID;

/** Row shape for {@code GET /portal/trust/matters/{matterId}/statement-documents}. */
public record PortalTrustStatementDocumentResponse(
    UUID id, String fileName, Instant generatedAt, String downloadUrl) {}
