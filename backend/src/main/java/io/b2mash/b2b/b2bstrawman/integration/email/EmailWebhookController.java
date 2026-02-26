package io.b2mash.b2b.b2bstrawman.integration.email;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/email")
public class EmailWebhookController {

  private final EmailWebhookService webhookService;

  public EmailWebhookController(EmailWebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @PostMapping("/{provider}")
  public ResponseEntity<Void> handleWebhook(
      @PathVariable String provider,
      @RequestBody String payload,
      @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Signature", required = false)
          String signature,
      @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Timestamp", required = false)
          String timestamp) {
    webhookService.processWebhook(provider, payload, signature, timestamp);
    return ResponseEntity.ok().build();
  }
}
