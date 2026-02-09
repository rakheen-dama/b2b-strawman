package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantFilter;
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
@RequestMapping("/internal/orgs")
public class PlanSyncController {

  private static final Logger log = LoggerFactory.getLogger(PlanSyncController.class);

  private final OrganizationRepository organizationRepository;
  private final TenantFilter tenantFilter;

  public PlanSyncController(
      OrganizationRepository organizationRepository, TenantFilter tenantFilter) {
    this.organizationRepository = organizationRepository;
    this.tenantFilter = tenantFilter;
  }

  @PostMapping("/plan-sync")
  public ResponseEntity<Void> syncPlan(@Valid @RequestBody PlanSyncRequest request) {
    log.info(
        "Received plan sync: clerkOrgId={}, planSlug={}", request.clerkOrgId(), request.planSlug());

    var org =
        organizationRepository
            .findByClerkOrgId(request.clerkOrgId())
            .orElseThrow(() -> new ResourceNotFoundException("Organization", request.clerkOrgId()));

    Tier tier = deriveTier(request.planSlug());
    org.updatePlan(tier, request.planSlug());
    organizationRepository.save(org);

    tenantFilter.evictSchema(request.clerkOrgId());

    log.info(
        "Plan synced: clerkOrgId={}, tier={}, planSlug={}",
        request.clerkOrgId(),
        tier,
        request.planSlug());

    return ResponseEntity.ok().build();
  }

  private Tier deriveTier(String planSlug) {
    if (planSlug != null && planSlug.contains("pro")) {
      return Tier.PRO;
    }
    return Tier.STARTER;
  }

  public record PlanSyncRequest(
      @NotBlank(message = "clerkOrgId is required") String clerkOrgId,
      @NotBlank(message = "planSlug is required") String planSlug) {}
}
