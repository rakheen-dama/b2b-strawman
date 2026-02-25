package io.b2mash.b2b.b2bstrawman.integration.email;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryLogService {

  private final EmailDeliveryLogRepository repository;

  public EmailDeliveryLogService(EmailDeliveryLogRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public EmailDeliveryLog record(
      String referenceType,
      UUID referenceId,
      String templateName,
      String recipientEmail,
      String providerSlug,
      SendResult result) {
    var status =
        result.success() ? EmailDeliveryStatus.SENT.name() : EmailDeliveryStatus.FAILED.name();
    var log =
        new EmailDeliveryLog(
            recipientEmail,
            templateName,
            referenceType,
            referenceId,
            status,
            result.providerMessageId(),
            providerSlug,
            result.errorMessage());
    return repository.save(log);
  }

  @Transactional
  public void updateStatus(
      String providerMessageId, EmailDeliveryStatus newStatus, String errorMessage) {
    repository
        .findByProviderMessageId(providerMessageId)
        .ifPresent(log -> log.updateDeliveryStatus(newStatus, errorMessage));
  }

  @Transactional(readOnly = true)
  public Page<EmailDeliveryLog> findByFilters(
      EmailDeliveryStatus status, Instant from, Instant to, Pageable pageable) {
    if (status != null) {
      return repository.findByStatusAndCreatedAtBetween(status.name(), from, to, pageable);
    }
    return repository.findByCreatedAtBetween(from, to, pageable);
  }

  @Transactional(readOnly = true)
  public EmailDeliveryStats getStats(String providerSlug, int hourlyLimit) {
    var now = Instant.now();
    var twentyFourHoursAgo = now.minus(24, ChronoUnit.HOURS);
    var sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
    var oneHourAgo = now.truncatedTo(ChronoUnit.HOURS);

    long sent24h = repository.countByCreatedAtAfter(twentyFourHoursAgo);
    long bounced7d =
        repository.countByStatusAndCreatedAtAfter(EmailDeliveryStatus.BOUNCED.name(), sevenDaysAgo);
    long failed7d =
        repository.countByStatusAndCreatedAtAfter(EmailDeliveryStatus.FAILED.name(), sevenDaysAgo);
    long rateLimited7d =
        repository.countByStatusAndCreatedAtAfter(
            EmailDeliveryStatus.RATE_LIMITED.name(), sevenDaysAgo);
    long currentHourUsage = repository.countByCreatedAtAfter(oneHourAgo);

    return new EmailDeliveryStats(
        sent24h, bounced7d, failed7d, rateLimited7d, currentHourUsage, hourlyLimit, providerSlug);
  }
}
