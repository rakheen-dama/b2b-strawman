package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.time.Instant;
import java.util.UUID;

public record PortalAcceptanceView(
    UUID id,
    UUID portalContactId,
    UUID generatedDocumentId,
    String documentTitle,
    String documentFileName,
    String status,
    String requestToken,
    Instant sentAt,
    Instant expiresAt,
    String orgName,
    String orgLogo,
    Instant createdAt) {}
