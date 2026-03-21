package io.b2mash.b2b.b2bstrawman.integration;

import io.b2mash.b2b.b2bstrawman.assistant.provider.ModelInfo;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/integrations/ai")
public class AiSettingsController {

  private final IntegrationService integrationService;

  public AiSettingsController(IntegrationService integrationService) {
    this.integrationService = integrationService;
  }

  @GetMapping("/models")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Map<String, List<ModelInfo>>> availableModels() {
    return ResponseEntity.ok(Map.of("models", integrationService.getAiModels()));
  }
}
