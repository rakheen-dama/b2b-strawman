package io.b2mash.b2b.b2bstrawman.proposal.dto;

public record ProposalStats(
    long totalDraft,
    long totalSent,
    long totalAccepted,
    long totalDeclined,
    long totalExpired,
    double conversionRate,
    double averageDaysToAccept) {}
