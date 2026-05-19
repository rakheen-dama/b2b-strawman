package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

/** Summary of a payment poll cycle for a single connection. */
public record PaymentPollSummary(int matched, int drifted, int skipped) {}
