package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationExecutionResponse;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AutomationExecutionController {

  private final AutomationRuleService automationRuleService;
  private final VerticalModuleGuard moduleGuard;

  public AutomationExecutionController(
      AutomationRuleService automationRuleService, VerticalModuleGuard moduleGuard) {
    this.automationRuleService = automationRuleService;
    this.moduleGuard = moduleGuard;
  }

  @GetMapping("/api/automation-executions")
  @RequiresCapability("AUTOMATIONS")
  public ResponseEntity<Page<AutomationExecutionResponse>> list(
      @RequestParam(required = false) UUID ruleId,
      @RequestParam(required = false) ExecutionStatus status,
      @PageableDefault(size = 20) Pageable pageable) {
    // Phase 62 named exception: log-read endpoints guarded at controller level — service path is
    // shared with execution engine
    moduleGuard.requireModule("automation_builder");
    return ResponseEntity.ok(automationRuleService.listExecutions(ruleId, status, pageable));
  }

  @GetMapping("/api/automation-executions/{id}")
  @RequiresCapability("AUTOMATIONS")
  public ResponseEntity<AutomationExecutionResponse> get(@PathVariable UUID id) {
    // Phase 62 named exception: log-read endpoints guarded at controller level — service path is
    // shared with execution engine
    moduleGuard.requireModule("automation_builder");
    return ResponseEntity.ok(automationRuleService.getExecution(id));
  }

  @GetMapping("/api/automation-rules/{ruleId}/executions")
  @RequiresCapability("AUTOMATIONS")
  public ResponseEntity<Page<AutomationExecutionResponse>> listForRule(
      @PathVariable UUID ruleId, @PageableDefault(size = 20) Pageable pageable) {
    // Phase 62 named exception: log-read endpoints guarded at controller level — service path is
    // shared with execution engine
    moduleGuard.requireModule("automation_builder");
    return ResponseEntity.ok(automationRuleService.listExecutionsForRule(ruleId, pageable));
  }
}
