package io.b2mash.b2b.b2bstrawman.integration.email;

/** Outcome of an email send attempt. */
public record SendResult(boolean success, String providerMessageId, String errorMessage) {}
