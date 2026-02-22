package io.b2mash.b2b.b2bstrawman.integration.signing;

/** Request to send a document for e-signature. */
public record SigningRequest(
    byte[] documentBytes,
    String contentType,
    String signerName,
    String signerEmail,
    String callbackUrl) {}
