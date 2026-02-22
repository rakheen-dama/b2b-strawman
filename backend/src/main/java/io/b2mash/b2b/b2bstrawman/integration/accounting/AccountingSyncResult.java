package io.b2mash.b2b.b2bstrawman.integration.accounting;

/** Result of syncing data to the external accounting system. */
public record AccountingSyncResult(
    boolean success, String externalReferenceId, String errorMessage) {}
