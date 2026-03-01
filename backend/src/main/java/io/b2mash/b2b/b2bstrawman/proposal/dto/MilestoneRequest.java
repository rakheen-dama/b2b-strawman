package io.b2mash.b2b.b2bstrawman.proposal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record MilestoneRequest(
    @NotBlank String description, @NotNull @Positive BigDecimal percentage, int relativeDueDays) {}
