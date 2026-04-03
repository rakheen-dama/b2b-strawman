package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.billing.AdminBillingDtos.AdminBillingOverrideRequest;
import io.b2mash.b2b.b2bstrawman.billing.AdminBillingDtos.AdminTenantBillingResponse;
import io.b2mash.b2b.b2bstrawman.billing.AdminBillingDtos.ExtendTrialRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform-admin/billing")
@PreAuthorize("@platformSecurityService.isPlatformAdmin()")
public class PlatformBillingController {

  private final AdminBillingService adminBillingService;

  public PlatformBillingController(AdminBillingService adminBillingService) {
    this.adminBillingService = adminBillingService;
  }

  @GetMapping("/tenants")
  public ResponseEntity<List<AdminTenantBillingResponse>> listTenants(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String billingMethod,
      @RequestParam(required = false) String profile,
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok(
        adminBillingService.listTenants(status, billingMethod, profile, search));
  }

  @GetMapping("/tenants/{orgId}")
  public ResponseEntity<AdminTenantBillingResponse> getTenant(@PathVariable UUID orgId) {
    return ResponseEntity.ok(adminBillingService.getTenant(orgId));
  }

  @PutMapping("/tenants/{orgId}/status")
  public ResponseEntity<AdminTenantBillingResponse> overrideBilling(
      @PathVariable UUID orgId, @Valid @RequestBody AdminBillingOverrideRequest request) {
    return ResponseEntity.ok(adminBillingService.overrideBilling(orgId, request));
  }

  @PostMapping("/tenants/{orgId}/extend-trial")
  public ResponseEntity<AdminTenantBillingResponse> extendTrial(
      @PathVariable UUID orgId, @Valid @RequestBody ExtendTrialRequest request) {
    return ResponseEntity.ok(adminBillingService.extendTrial(orgId, request.days()));
  }
}
