package io.b2mash.b2b.b2bstrawman.integration.accounting;

/** Request to sync a customer record to the external accounting system. */
public record CustomerSyncRequest(
    String customerName,
    String email,
    String addressLine1,
    String addressLine2,
    String city,
    String postalCode,
    String country) {}
