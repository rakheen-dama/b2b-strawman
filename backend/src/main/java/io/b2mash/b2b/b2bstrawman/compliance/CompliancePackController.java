package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compliance-packs")
public class CompliancePackController {

  private final CompliancePackService compliancePackService;

  public CompliancePackController(CompliancePackService compliancePackService) {
    this.compliancePackService = compliancePackService;
  }

  @GetMapping("/{packId}")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<CompliancePackDefinition> getPackDefinition(@PathVariable String packId) {
    return ResponseEntity.ok(compliancePackService.getPackDefinition(packId));
  }
}
