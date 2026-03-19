package io.b2mash.b2b.b2bstrawman.datarequest;

import java.time.Instant;
import java.util.UUID;

/** Status response returned after a completed data export, including the presigned download URL. */
public record ExportStatusResponse(
    UUID exportId,
    String status,
    String downloadUrl,
    Instant expiresAt,
    int fileCount,
    long totalSizeBytes,
    String s3Key) {}
