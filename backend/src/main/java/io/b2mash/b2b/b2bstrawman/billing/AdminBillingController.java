package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.provisioning.TenantUpgradeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/billing")
public class AdminBillingController {

  private static final Logger log = LoggerFactory.getLogger(AdminBillingController.class);

  private final SubscriptionService subscriptionService;
  private final TenantUpgradeService tenantUpgradeService;

  public AdminBillingController(
      SubscriptionService subscriptionService, TenantUpgradeService tenantUpgradeService) {
    this.subscriptionService = subscriptionService;
    this.tenantUpgradeService = tenantUpgradeService;
  }

  @PostMapping("/set-plan")
  public ResponseEntity<Void> setPlan(@Valid @RequestBody SetPlanRequest request) {
    log.info(
        "Received set-plan: clerkOrgId={}, planSlug={}", request.clerkOrgId(), request.planSlug());

    var result = subscriptionService.changePlan(request.clerkOrgId(), request.planSlug());

    if (result.upgradeNeeded()) {
      log.info("Tier upgrade detected for org {}, starting migration", request.clerkOrgId());
      tenantUpgradeService.upgrade(request.clerkOrgId());
    }

    return ResponseEntity.ok().build();
  }

  public record SetPlanRequest(
      @NotBlank(message = "clerkOrgId is required") String clerkOrgId,
      @NotBlank(message = "planSlug is required") String planSlug) {}
}
