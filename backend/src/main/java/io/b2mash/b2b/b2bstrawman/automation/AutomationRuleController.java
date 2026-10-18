package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationRuleResponse;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Always-on admin surface for automation rules (ADR-334): list, read and enable/disable. Rules are
 * installed by packs ({@code AutomationPackInstaller}); the rule builder's write endpoints (create,
 * update, delete, duplicate, test, and the action sub-resource) were removed in Phase 86 (Epic
 * 604A).
 */
@RestController
public class AutomationRuleController {

  private final AutomationRuleService automationRuleService;

  public AutomationRuleController(AutomationRuleService automationRuleService) {
    this.automationRuleService = automationRuleService;
  }

  @GetMapping("/api/automation-rules")
  @RequiresCapability("AUTOMATIONS")
  public ResponseEntity<List<AutomationRuleResponse>> list(
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) TriggerType triggerType) {
    return ResponseEntity.ok(automationRuleService.listRules(enabled, triggerType));
  }

  @GetMapping("/api/automation-rules/{id}")
  @RequiresCapability("AUTOMATIONS")
  public ResponseEntity<AutomationRuleResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(automationRuleService.getRule(id));
  }

  @PostMapping("/api/automation-rules/{id}/toggle")
  @RequiresCapability("AUTOMATIONS")
  public ResponseEntity<AutomationRuleResponse> toggle(@PathVariable UUID id) {
    return ResponseEntity.ok(automationRuleService.toggleRule(id));
  }
}
