package io.b2mash.b2b.b2bstrawman.proposal;

import java.util.List;
import java.util.UUID;

/**
 * Published after proposal acceptance orchestration completes successfully. Consumed by
 * ProposalAcceptedEventHandler for post-commit side effects (notifications, portal sync).
 */
public record ProposalAcceptedEvent(
    UUID proposalId,
    UUID createdProjectId,
    String proposalNumber,
    String customerName,
    String projectName,
    List<UUID> teamMemberIds,
    UUID creatorMemberId,
    String tenantId,
    String orgId) {}
