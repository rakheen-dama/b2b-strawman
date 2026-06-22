package io.b2mash.b2b.b2bstrawman.crm.dto;

import io.b2mash.b2b.b2bstrawman.proposal.FeeModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create-a-proposal-from-a-deal request (Phase 80, 576A). The deal supplies {@code customerId} and
 * pre-fills the proposal value, so the body only carries the proposal-specific fields:
 *
 * <ul>
 *   <li>{@code title} — proposal title (required).
 *   <li>{@code feeModel} — required ({@code ProposalService.createProposal} has no default).
 *   <li>{@code fixedFeeAmount}/{@code fixedFeeCurrency} — required for FIXED.
 *   <li>{@code retainerAmount}/{@code retainerCurrency}/{@code retainerHoursIncluded} — for
 *       RETAINER.
 * </ul>
 *
 * <p>Fee-field validation (e.g. FIXED requires {@code fixedFeeAmount > 0}) is enforced downstream
 * by {@code ProposalService.createProposal}.
 *
 * @param title proposal title (required, max 200 chars)
 * @param feeModel fee model (required)
 * @param fixedFeeAmount fixed-fee amount (required for FIXED)
 * @param fixedFeeCurrency fixed-fee currency code (for FIXED)
 * @param retainerAmount retainer amount (required for RETAINER)
 * @param retainerCurrency retainer currency code (for RETAINER)
 * @param retainerHoursIncluded retainer hours included (for RETAINER)
 */
public record CreateDealProposalRequest(
    @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title,
    @NotNull(message = "feeModel is required") FeeModel feeModel,
    BigDecimal fixedFeeAmount,
    String fixedFeeCurrency,
    BigDecimal retainerAmount,
    String retainerCurrency,
    BigDecimal retainerHoursIncluded) {}
