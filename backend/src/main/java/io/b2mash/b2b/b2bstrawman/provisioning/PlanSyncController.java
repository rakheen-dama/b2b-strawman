package io.b2mash.b2b.b2bstrawman.provisioning;

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

  private final PlanSyncService planSyncService;

  public PlanSyncController(PlanSyncService planSyncService) {
    this.planSyncService = planSyncService;
  }

  @PostMapping("/plan-sync")
  public ResponseEntity<Void> syncPlan(@Valid @RequestBody PlanSyncRequest request) {
    log.info(
        "Received plan sync: clerkOrgId={}, planSlug={}", request.clerkOrgId(), request.planSlug());

    planSyncService.syncPlan(request.clerkOrgId(), request.planSlug());

    return ResponseEntity.ok().build();
  }

  public record PlanSyncRequest(
      @NotBlank(message = "clerkOrgId is required") String clerkOrgId,
      @NotBlank(message = "planSlug is required") String planSlug) {}
}
