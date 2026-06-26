package io.b2mash.b2b.b2bstrawman.correspondence;

import java.util.UUID;

/**
 * Immutable scope of an inbound {@link Correspondence} — the minimal slice the {@code
 * attach_document} MCP tool needs to choose CUSTOMER vs PROJECT upload scope, without leaking the
 * JPA entity across the MCP/service boundary. The 581A linkage CHECK guarantees at least one of
 * {@code customerId} / {@code projectId} is non-null.
 */
public record CorrespondenceScope(UUID correspondenceId, UUID customerId, UUID projectId) {

  static CorrespondenceScope of(Correspondence correspondence) {
    return new CorrespondenceScope(
        correspondence.getId(), correspondence.getCustomerId(), correspondence.getProjectId());
  }
}
