package io.b2mash.b2b.b2bstrawman.proposal.dto;

import io.b2mash.b2b.b2bstrawman.proposal.ProposalStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProposalSummaryDto(
    int total,
    Map<ProposalStatus, Integer> byStatus,
    double avgDaysToAcceptance,
    double conversionRate,
    List<OverdueProposalDto> pendingOverdue) {

  public record OverdueProposalDto(
      UUID id,
      String title,
      String customerName,
      String projectName,
      Instant sentAt,
      long daysSinceSent) {}
}
