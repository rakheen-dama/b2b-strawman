package io.b2mash.b2b.b2bstrawman.portal;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Job handler for cleaning up expired magic link tokens for a single tenant. Directly uses {@link
 * MagicLinkTokenRepository} to delete tokens that expired more than 24 hours ago.
 *
 * <p>Extracted from {@link MagicLinkCleanupService#cleanupExpiredTokens()}.
 */
@Component
public class MagicLinkCleanupHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(MagicLinkCleanupHandler.class);

  private final MagicLinkTokenRepository tokenRepository;
  private final TransactionTemplate transactionTemplate;

  public MagicLinkCleanupHandler(
      MagicLinkTokenRepository tokenRepository, TransactionTemplate transactionTemplate) {
    this.tokenRepository = tokenRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public String jobType() {
    return "magic_link_cleanup";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
    Integer deleted =
        transactionTemplate.execute(status -> tokenRepository.deleteByExpiresAtBefore(cutoff));
    if (deleted != null && deleted > 0) {
      log.info("MagicLinkCleanupHandler: deleted {} expired tokens", deleted);
    }
  }
}
