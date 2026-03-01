package io.b2mash.b2b.b2bstrawman.proposal.dto;

import java.math.BigDecimal;

public record MilestoneRequest(String description, BigDecimal percentage, int relativeDueDays) {}
