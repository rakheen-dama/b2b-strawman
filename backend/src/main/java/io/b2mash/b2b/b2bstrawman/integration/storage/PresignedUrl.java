package io.b2mash.b2b.b2bstrawman.integration.storage;

import java.time.Instant;

/** Represents a time-limited presigned URL for storage operations. */
public record PresignedUrl(String url, Instant expiresAt) {}
