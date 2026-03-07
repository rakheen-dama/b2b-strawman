package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationExecutionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AutomationExecutionController {

  private final AutomationRuleService automationRuleService;

  public AutomationExecutionController(AutomationRuleService automationRuleService) {
    this.automationRuleService = automationRuleService;
  }

  @GetMapping("/api/automation-executions")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<AutomationExecutionResponse>> list(
      @RequestParam(required = false) UUID ruleId,
      @RequestParam(required = false) ExecutionStatus status) {
    return ResponseEntity.ok(automationRuleService.listExecutions(ruleId, status));
  }

  @GetMapping("/api/automation-executions/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AutomationExecutionResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(automationRuleService.getExecution(id));
  }

  @GetMapping("/api/automation-rules/{ruleId}/executions")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<AutomationExecutionResponse>> listForRule(@PathVariable UUID ruleId) {
    return ResponseEntity.ok(automationRuleService.listExecutionsForRule(ruleId));
  }
}
