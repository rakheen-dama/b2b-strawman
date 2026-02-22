package io.b2mash.b2b.b2bstrawman.integration;

/** Shared result record for testing connectivity to an external provider. */
public record ConnectionTestResult(boolean success, String providerName, String errorMessage) {}
