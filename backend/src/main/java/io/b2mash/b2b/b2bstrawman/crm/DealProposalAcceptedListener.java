package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.proposal.ProposalAcceptedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Win-loop glue (Phase 80, slice 576A). When a proposal that is linked to a CRM deal is accepted,
 * this AFTER_COMMIT listener flips that deal to WON (idempotently) so the existing {@code
 * DealWonEventHandler} sends the DEAL_WON notification off the re-published {@code DealWonEvent}.
 *
 * <p>The listener is intentionally thin: it re-binds tenant scope (AFTER_COMMIT listeners run
 * outside request scope) and delegates the actual load + {@code markWon} + save + audit + event to
 * {@link DealProposalService#markWonFromProposalAcceptance}, which is {@code @Transactional} so the
 * write commits in its own transaction (the listener itself has none — mirrors {@code
 * AccountingSyncEventListener}).
 *
 * <p>{@link ProposalAcceptedEvent} carries no {@code shardId}, so the listener uses {@code
 * runForTenant} (2-arg); {@code getShardIdOrDefault()} then returns {@code "primary"} for the
 * re-published {@code DealWonEvent}, matching what {@code DealWonEventHandler} expects.
 *
 * <p>Scope: flipping the deal to WON is this listener's <strong>only</strong> job. It does not
 * nudge the customer lifecycle or send notifications directly — proposal acceptance and {@code
 * DealWonEventHandler} already do those (ADR-315). Already-WON deals are a no-op.
 */
@Component
public class DealProposalAcceptedListener {

  private static final Logger log = LoggerFactory.getLogger(DealProposalAcceptedListener.class);

  private final DealProposalService dealProposalService;

  public DealProposalAcceptedListener(DealProposalService dealProposalService) {
    this.dealProposalService = dealProposalService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProposalAccepted(ProposalAcceptedEvent event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            dealProposalService.markWonFromProposalAcceptance(event.proposalId());
          } catch (Exception e) {
            // The proposal acceptance has already committed; a win-glue failure must not propagate.
            log.error(
                "Failed to apply win-loop glue for accepted proposal {}", event.proposalId(), e);
          }
        });
  }
}
