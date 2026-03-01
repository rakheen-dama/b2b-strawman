package io.b2mash.b2b.b2bstrawman.proposal;

import java.util.UUID;

/**
 * Published when proposal acceptance orchestration fails. Used to notify the proposal creator of
 * the failure.
 */
public record ProposalOrchestrationFailedEvent(
    UUID proposalId,
    String proposalNumber,
    UUID creatorMemberId,
    String errorMessage,
    String tenantId,
    String orgId) {}
