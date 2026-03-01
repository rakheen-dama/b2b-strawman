package io.b2mash.b2b.b2bstrawman.proposal;

import java.util.UUID;

/**
 * Published when a proposal expires (SENT past expiresAt deadline). Consumed by
 * ProposalExpiredEventHandler for post-commit side effects: in-app notification to creator, email
 * to portal contact, and portal status sync.
 */
public record ProposalExpiredEvent(
    UUID proposalId,
    String proposalNumber,
    String customerName,
    UUID createdByMemberId,
    String portalContactEmail,
    String orgName,
    String tenantId,
    String orgId) {}
