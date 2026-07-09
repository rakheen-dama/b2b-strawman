package io.b2mash.b2b.b2bstrawman.customer.dto;

import java.util.UUID;

/** Minimal echo of the collections-exemption flag after a set/clear (Phase 83, §2.3). */
public record CollectionsExemptionResponse(UUID id, boolean collectionsExempt) {}
