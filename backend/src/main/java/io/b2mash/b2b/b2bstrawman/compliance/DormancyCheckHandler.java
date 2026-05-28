package io.b2mash.b2b.b2bstrawman.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for executing dormancy checks for a single tenant. Delegates to {@link
 * DormancyScheduledJob#processTenant()} which checks inactive customers and transitions them to
 * DORMANT status, then notifies admins.
 *
 * <p>Extracted from {@link DormancyScheduledJob#executeDormancyCheck()}.
 */
@Component
public class DormancyCheckHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(DormancyCheckHandler.class);

  private final DormancyScheduledJob dormancyScheduledJob;

  public DormancyCheckHandler(DormancyScheduledJob dormancyScheduledJob) {
    this.dormancyScheduledJob = dormancyScheduledJob;
  }

  @Override
  public String jobType() {
    return "dormancy_check";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int transitioned = dormancyScheduledJob.processTenant();
    if (transitioned > 0) {
      log.info("DormancyCheckHandler: transitioned {} customers to dormant", transitioned);
    }
  }
}
