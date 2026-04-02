package io.b2mash.b2b.b2bstrawman.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal admin billing endpoint. The set-plan endpoint is a no-op stub after Epic 419A removed
 * the Tier/planSlug model. Full subscription lifecycle management will be in Epic 420.
 */
@RestController
@RequestMapping("/internal/billing")
public class AdminBillingController {

  private static final Logger log = LoggerFactory.getLogger(AdminBillingController.class);

  public AdminBillingController() {}

  @PostMapping("/set-plan")
  public ResponseEntity<Void> setPlan(@Valid @RequestBody SetPlanRequest request) {
    log.info(
        "Received set-plan: clerkOrgId={}, planSlug={} (no-op — Tier model removed)",
        request.clerkOrgId(),
        request.planSlug());

    return ResponseEntity.ok().build();
  }

  public record SetPlanRequest(
      @NotBlank(message = "clerkOrgId is required") String clerkOrgId,
      @NotBlank(message = "planSlug is required") String planSlug) {}
}
