package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final TransactionTemplate transactionTemplate;

  public MagicLinkCleanupService(
      MagicLinkTokenRepository tokenRepository,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      TransactionTemplate transactionTemplate) {
    this.tokenRepository = tokenRepository;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(fixedRate = 3600000) // hourly
  public void cleanupExpiredTokens() {
    Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
    int totalDeleted = 0;

    var mappings = orgSchemaMappingRepository.findAll();
    for (var mapping : mappings) {
      String schema = mapping.getSchemaName();
      try {
        Integer deleted =
            ScopedValue.where(RequestScopes.TENANT_ID, schema)
                .call(
                    () ->
                        transactionTemplate.execute(
                            status -> tokenRepository.deleteByExpiresAtBefore(cutoff)));
        if (deleted != null && deleted > 0) {
          totalDeleted += deleted;
        }
      } catch (Exception e) {
        log.warn("Failed to clean up expired tokens in schema {}: {}", schema, e.getMessage());
      }
    }

    if (totalDeleted > 0) {
      log.info(
          "Cleaned up {} expired magic link tokens across {} schemas",
          totalDeleted,
          mappings.size());
    }
  }
}
