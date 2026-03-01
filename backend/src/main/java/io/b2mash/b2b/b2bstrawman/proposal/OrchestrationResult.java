package io.b2mash.b2b.b2bstrawman.proposal;

import java.util.List;
import java.util.UUID;

/**
 * Result of proposal acceptance orchestration. Contains references to all entities created during
 * the orchestration process.
 *
 * @param proposalId the accepted proposal's ID
 * @param projectId the created project's ID
 * @param assignedMemberIds IDs of team members assigned to the project
 * @param createdInvoiceIds IDs of DRAFT invoices created (empty for HOURLY fee model)
 */
public record OrchestrationResult(
    UUID proposalId, UUID projectId, List<UUID> assignedMemberIds, List<UUID> createdInvoiceIds) {}
