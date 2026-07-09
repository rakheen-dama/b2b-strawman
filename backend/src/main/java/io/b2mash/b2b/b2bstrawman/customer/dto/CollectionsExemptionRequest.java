package io.b2mash.b2b.b2bstrawman.customer.dto;

/** Request to set/clear the per-customer collections exemption (Phase 83, §2.3). */
public record CollectionsExemptionRequest(boolean collectionsExempt) {}
