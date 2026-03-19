package io.b2mash.b2b.b2bstrawman.datarequest;

import java.util.UUID;

/** Result of initiating a data export operation. */
public record ExportResult(UUID exportId, String status, int estimatedFiles) {}
