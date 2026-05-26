package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled cleanup of expired magic link tokens. Runs hourly and removes tokens that expired more
 * than 24 hours ago across all tenant schemas, giving a buffer for any audit or debugging needs.
 */
@Component
public class MagicLinkCleanupService {

  private static final Logger log = LoggerFactory.getLogger(MagicLinkCleanupService.class);

  private final MagicLinkTokenRepository tokenRepository;
  private final TenantScopedRunner tenantScopedRunner;
  private final TransactionTemplate transactionTemplate;

  public MagicLinkCleanupService(
      MagicLinkTokenRepository tokenRepository,
      TenantScopedRunner tenantScopedRunner,
      TransactionTemplate transactionTemplate) {
    this.tokenRepository = tokenRepository;
    this.tenantScopedRunner = tenantScopedRunner;
    this.transactionTemplate = transactionTemplate;
  }

  @SchedulerLock(name = "magic_link_cleanup_expired_tokens", lockAtLeastFor = "30m")
  @Scheduled(fixedRate = 3600000) // hourly
  public void cleanupExpiredTokens() {
    Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
    int[] totalDeleted = {0};
    int tenantsProcessed =
        tenantScopedRunner.forEachTenant(
            (tenantId, orgId) -> {
              Integer deleted =
                  transactionTemplate.execute(
                      status -> tokenRepository.deleteByExpiresAtBefore(cutoff));
              if (deleted != null && deleted > 0) {
                totalDeleted[0] += deleted;
              }
            });

    if (totalDeleted[0] > 0) {
      log.info(
          "Cleaned up {} expired magic link tokens across {} schemas",
          totalDeleted[0],
          tenantsProcessed);
    }
  }
}
