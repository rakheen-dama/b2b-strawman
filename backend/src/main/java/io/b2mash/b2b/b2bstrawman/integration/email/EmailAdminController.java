package io.b2mash.b2b.b2bstrawman.integration.email;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin endpoints for email delivery monitoring, statistics, and test email sending. */
@RestController
@RequestMapping("/api/email")
public class EmailAdminController {

  private final EmailAdminService emailAdminService;

  public EmailAdminController(EmailAdminService emailAdminService) {
    this.emailAdminService = emailAdminService;
  }

  @GetMapping("/delivery-log")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Page<EmailDeliveryLogResponse>> getDeliveryLog(
      @RequestParam(required = false) EmailDeliveryStatus status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      Pageable pageable) {
    return ResponseEntity.ok(emailAdminService.getDeliveryLog(status, from, to, pageable));
  }

  @GetMapping("/stats")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<EmailDeliveryStats> getStats() {
    return ResponseEntity.ok(emailAdminService.getStats());
  }

  @PostMapping("/test")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<EmailDeliveryLogResponse> sendTestEmail() {
    return ResponseEntity.ok(emailAdminService.sendTestEmail());
  }
}
