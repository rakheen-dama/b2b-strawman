package io.b2mash.b2b.b2bstrawman.integration.signing;

/** Result of sending a document for e-signature. */
public record SigningResult(boolean success, String signingReference, String errorMessage) {}
