package io.b2mash.b2b.b2bstrawman.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookIdempotencyService {

  private static final Logger log = LoggerFactory.getLogger(WebhookIdempotencyService.class);

  private final ProcessedWebhookRepository repository;

  public WebhookIdempotencyService(ProcessedWebhookRepository repository) {
    this.repository = repository;
  }

  public boolean isAlreadyProcessed(String svixId) {
    return repository.existsById(svixId);
  }

  @Transactional
  public void markProcessed(String svixId, String eventType) {
    if (repository.existsById(svixId)) {
      log.debug("Webhook {} already marked as processed, skipping", svixId);
      return;
    }
    repository.save(new ProcessedWebhook(svixId, eventType));
    log.info("Marked webhook {} ({}) as processed", svixId, eventType);
  }
}
