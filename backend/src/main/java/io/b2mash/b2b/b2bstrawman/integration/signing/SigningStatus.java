package io.b2mash.b2b.b2bstrawman.integration.signing;

import java.time.Instant;

/** Current status of a document signing request. */
public record SigningStatus(SigningState state, String signingReference, Instant updatedAt) {}
