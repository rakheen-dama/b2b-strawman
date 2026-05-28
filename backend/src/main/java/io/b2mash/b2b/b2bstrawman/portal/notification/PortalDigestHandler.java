package io.b2mash.b2b.b2bstrawman.portal.notification;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for dispatching portal digest emails for a single tenant. Delegates to {@link
 * PortalDigestScheduler#processTenant(PortalDigestScheduler.RunOptions)} with {@link
 * PortalDigestScheduler.RunOptions#full()} to perform the standard weekly digest send including
 * cadence checks, contact preference filtering, and content assembly.
 *
 * <p>Extracted from {@link PortalDigestScheduler#scheduledRun()}.
 */
@Component
public class PortalDigestHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(PortalDigestHandler.class);

  private final PortalDigestScheduler portalDigestScheduler;

  public PortalDigestHandler(PortalDigestScheduler portalDigestScheduler) {
    this.portalDigestScheduler = portalDigestScheduler;
  }

  @Override
  public String jobType() {
    return "portal_digest";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    log.debug("PortalDigestHandler: executing digest for tenant");
    portalDigestScheduler.processTenant(PortalDigestScheduler.RunOptions.full());
  }
}
