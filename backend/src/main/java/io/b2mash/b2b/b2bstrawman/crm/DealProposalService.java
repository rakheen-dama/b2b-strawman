package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.crm.dto.CreateDealProposalRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.LinkedProposalDto;
import io.b2mash.b2b.b2bstrawman.crm.event.DealWonEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.proposal.Proposal;
import io.b2mash.b2b.b2bstrawman.proposal.ProposalRepository;
import io.b2mash.b2b.b2bstrawman.proposal.ProposalService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deal↔Proposal linking service (Phase 80, slice 576A). Three operations:
 *
 * <ul>
 *   <li>{@link #createFromDeal} — draft a proposal pre-filled from a deal (customer + value), then
 *       link it back to the deal via {@code Proposal.dealId}.
 *   <li>{@link #linkExisting} — link an already-existing proposal to a deal (works on any proposal
 *       status; {@code setDealId} is ungated).
 *   <li>{@link #listForDeal} — list the proposals linked to a deal as {@link LinkedProposalDto}.
 * </ul>
 *
 * <p>Proposal drafting is delegated to {@link ProposalService#createProposal} — this service never
 * re-implements proposal validation or number allocation. The {@code proposals.deal_id} link is a
 * plain mapped column (no JPA association); a deal can have many proposals.
 */
@Service
public class DealProposalService {

  private static final Logger log = LoggerFactory.getLogger(DealProposalService.class);

  private final DealRepository dealRepository;
  private final ProposalService proposalService;
  private final ProposalRepository proposalRepository;
  private final PipelineStageService pipelineStageService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public DealProposalService(
      DealRepository dealRepository,
      ProposalService proposalService,
      ProposalRepository proposalRepository,
      PipelineStageService pipelineStageService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.dealRepository = dealRepository;
    this.proposalService = proposalService;
    this.proposalRepository = proposalRepository;
    this.pipelineStageService = pipelineStageService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Drafts a proposal from a deal: the deal supplies {@code customerId} and pre-fills the headline
   * value (FIXED → {@code fixedFeeAmount}, RETAINER → {@code retainerAmount}) when the request
   * omits it, then links the new proposal back to the deal. Returns the linked-proposal read-model
   * view.
   */
  @Transactional
  public LinkedProposalDto createFromDeal(
      UUID dealId, CreateDealProposalRequest request, UUID createdById) {
    var deal = dealRepository.findOneById(dealId);

    // Pre-fill the headline fee amount from the deal value when the request omits it.
    var fixedFeeAmount = request.fixedFeeAmount();
    var retainerAmount = request.retainerAmount();
    switch (request.feeModel()) {
      case FIXED -> {
        if (fixedFeeAmount == null) {
          fixedFeeAmount = deal.getValueAmount();
        }
      }
      case RETAINER -> {
        if (retainerAmount == null) {
          retainerAmount = deal.getValueAmount();
        }
      }
      case HOURLY, CONTINGENCY -> {
        // no headline amount to pre-fill
      }
    }

    var proposal =
        proposalService.createProposal(
            request.title(),
            deal.getCustomerId(),
            request.feeModel(),
            createdById,
            null, // portalContactId
            fixedFeeAmount,
            request.fixedFeeCurrency(),
            null, // hourlyRateNote
            retainerAmount,
            request.retainerCurrency(),
            request.retainerHoursIncluded(),
            null, // contingencyPercent
            null, // contingencyCapPercent
            null, // contingencyDescription
            null, // contentJson (seeded by ProposalService)
            null, // projectTemplateId
            null); // expiresAt

    var saved = proposalService.linkToDeal(proposal.getId(), dealId);
    log.info("Created proposal {} from deal {}", saved.getId(), dealId);
    return LinkedProposalDto.from(saved);
  }

  /**
   * Links an existing proposal to a deal. Both must exist in the current tenant; the proposal may
   * be in any status ({@code setDealId} is ungated). The proposal and deal must belong to the same
   * customer — linking across customers would corrupt the deal's linked-proposal read model, so a
   * mismatch is rejected with a {@code 409}.
   *
   * <p>Re-linking a proposal that is already attached to a different deal is allowed (the previous
   * link is overwritten) but logged at {@code WARN} so the overwrite is visible to operators.
   */
  @Transactional
  public void linkExisting(UUID dealId, UUID proposalId) {
    Deal deal =
        dealRepository.findOneById(dealId); // validate the deal exists (throws 404 otherwise)
    Proposal proposal = proposalService.getProposal(proposalId);
    if (!deal.getCustomerId().equals(proposal.getCustomerId())) {
      throw new ResourceConflictException(
          "Customer mismatch",
          "Proposal %s belongs to a different customer than deal %s — they cannot be linked."
              .formatted(proposalId, dealId));
    }
    UUID existingDealId = proposal.getDealId();
    if (existingDealId != null && !existingDealId.equals(dealId)) {
      log.warn(
          "Re-linking proposal {} from deal {} to deal {} (previous link overwritten)",
          proposalId,
          existingDealId,
          dealId);
    }
    proposalService.linkToDeal(proposalId, dealId);
    log.info("Linked proposal {} to deal {}", proposalId, dealId);
  }

  /** Lists the proposals linked to a deal as {@link LinkedProposalDto}. */
  @Transactional(readOnly = true)
  public List<LinkedProposalDto> listForDeal(UUID dealId) {
    dealRepository.findOneById(dealId); // validate the deal exists (throws 404 otherwise)
    return proposalRepository.findByDealId(dealId).stream().map(LinkedProposalDto::from).toList();
  }

  /**
   * Win-loop (576A): flips the deal linked to an accepted proposal to WON, idempotently. Invoked by
   * {@link DealProposalAcceptedListener} AFTER_COMMIT — this {@code @Transactional} method opens
   * its own transaction so the load + {@code markWon} + save + audit commit atomically (the
   * listener itself runs outside any transaction).
   *
   * <p>No-op when the proposal has no linked deal or the deal is already WON (no double-win, no
   * second {@code deal.won} audit row). Mirrors {@code DealTransitionService}'s WON branch:
   * lowercase {@code "deal"} audit entityType + a re-published {@link DealWonEvent} (carrying
   * {@code shardId} from {@link RequestScopes#getShardIdOrDefault()}) that drives the existing
   * DEAL_WON notification. The customer-lifecycle nudge is intentionally NOT repeated here —
   * proposal acceptance already did it (ADR-315).
   *
   * <p>{@code REQUIRES_NEW} (empirically verified): this runs from the proposal-acceptance
   * AFTER_COMMIT callback, after the outer transaction has committed. A fresh transaction is
   * required for the win-flip to actually commit. This was reproduced — with a plain {@code
   * REQUIRED} transaction opened during AFTER_COMMIT cleanup the win-flip was NOT persisted (the
   * post-acceptance assertion read the deal as still OPEN); switching to {@code REQUIRES_NEW} made
   * the deal commit as WON. Do not revert to plain {@code @Transactional}.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markWonFromProposalAcceptance(UUID proposalId) {
    dealRepository
        .findByLinkedProposalId(proposalId)
        .ifPresent(
            deal -> {
              if (deal.getStatus() == DealStatus.WON) {
                log.debug(
                    "Deal {} already WON — win-loop no-op for proposal {}",
                    deal.getId(),
                    proposalId);
                return;
              }
              var wonStage = pipelineStageService.firstWonStage();
              deal.markWon(wonStage.getId(), Instant.now());
              dealRepository.save(deal);
              auditService.log(
                  AuditEventBuilder.builder()
                      .eventType("deal.won")
                      .entityType("deal")
                      .entityId(deal.getId())
                      .details(
                          Map.of(
                              "via", "proposal_acceptance", "proposal_id", proposalId.toString()))
                      .build());
              eventPublisher.publishEvent(
                  new DealWonEvent(
                      deal.getId(),
                      deal.getCustomerId(),
                      deal.getOwnerId(),
                      RequestScopes.requireTenantId(),
                      RequestScopes.requireOrgId(),
                      RequestScopes.getShardIdOrDefault()));
              log.info(
                  "Win-loop: marked deal {} WON via acceptance of proposal {}",
                  deal.getId(),
                  proposalId);
            });
  }
}
