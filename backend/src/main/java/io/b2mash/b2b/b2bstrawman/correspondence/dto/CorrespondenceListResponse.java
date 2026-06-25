package io.b2mash.b2b.b2bstrawman.correspondence.dto;

import io.b2mash.b2b.b2bstrawman.correspondence.Direction;
import java.time.Instant;
import java.util.UUID;

/**
 * List-row projection for the matter/customer correspondence tab. Consumed by 586A's controller.
 */
public record CorrespondenceListResponse(
    UUID id,
    String subject,
    String fromAddress,
    Instant receivedAt,
    long attachmentCount,
    Direction direction) {}
