package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortalInvoiceLineView(
    UUID id,
    UUID portalInvoiceId,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal amount,
    int sortOrder,
    Instant syncedAt) {}
