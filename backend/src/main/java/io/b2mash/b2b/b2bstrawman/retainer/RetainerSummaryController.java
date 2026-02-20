package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.retainer.dto.RetainerSummaryResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// SecurityConfig covers /api/** with .authenticated() — @PreAuthorize handles fine-grained auth.
@RestController
@RequestMapping("/api/customers")
public class RetainerSummaryController {

  private final RetainerAgreementService retainerAgreementService;

  public RetainerSummaryController(RetainerAgreementService retainerAgreementService) {
    this.retainerAgreementService = retainerAgreementService;
  }

  @GetMapping("/{customerId}/retainer-summary")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RetainerSummaryResponse> getRetainerSummary(@PathVariable UUID customerId) {
    return ResponseEntity.ok(retainerAgreementService.getRetainerSummary(customerId));
  }
}
