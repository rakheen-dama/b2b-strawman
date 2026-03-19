package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.datarequest.PaiaManualGenerationService.PaiaGenerateResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class DataProtectionController {

  private final PaiaManualGenerationService paiaManualGenerationService;

  public DataProtectionController(PaiaManualGenerationService paiaManualGenerationService) {
    this.paiaManualGenerationService = paiaManualGenerationService;
  }

  @PostMapping("/paia-manual/generate")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<PaiaGenerateResponse> generatePaiaManual() {
    UUID memberId = RequestScopes.requireMemberId();
    return ResponseEntity.ok(paiaManualGenerationService.generate(memberId));
  }
}
