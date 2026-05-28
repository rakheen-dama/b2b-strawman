package io.b2mash.b2b.b2bstrawman.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for expiring overdue proposals for a single tenant. Delegates to {@link
 * ProposalExpiryProcessor#processExpiredForTenant(String)} which finds SENT proposals past their
 * deadline, transitions them to EXPIRED, logs audit events, and publishes expiry notifications.
 *
 * <p>Extracted from {@link ProposalExpiryProcessor#processExpiredProposals()}.
 */
@Component
public class ProposalExpiryHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(ProposalExpiryHandler.class);

  private final ProposalExpiryProcessor expiryProcessor;

  public ProposalExpiryHandler(ProposalExpiryProcessor expiryProcessor) {
    this.expiryProcessor = expiryProcessor;
  }

  @Override
  public String jobType() {
    return "proposal_expiry";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    String orgId = RequestScopes.getOrgIdOrNull();
    int expired = expiryProcessor.processExpiredForTenant(orgId);
    if (expired > 0) {
      log.info("ProposalExpiryHandler: expired {} proposals", expired);
    }
  }
}
