package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationActionResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.CreateActionRequest;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.ReorderActionsRequest;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.UpdateActionRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AutomationActionController {

  private final AutomationRuleService automationRuleService;

  public AutomationActionController(AutomationRuleService automationRuleService) {
    this.automationRuleService = automationRuleService;
  }

  @PostMapping("/api/automation-rules/{ruleId}/actions")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AutomationActionResponse> addAction(
      @PathVariable UUID ruleId, @Valid @RequestBody CreateActionRequest request) {
    var response = automationRuleService.addAction(ruleId, request);
    return ResponseEntity.created(
            URI.create("/api/automation-rules/" + ruleId + "/actions/" + response.id()))
        .body(response);
  }

  @PutMapping("/api/automation-rules/{ruleId}/actions/{actionId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AutomationActionResponse> updateAction(
      @PathVariable UUID ruleId,
      @PathVariable UUID actionId,
      @Valid @RequestBody UpdateActionRequest request) {
    return ResponseEntity.ok(automationRuleService.updateAction(ruleId, actionId, request));
  }

  @DeleteMapping("/api/automation-rules/{ruleId}/actions/{actionId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> removeAction(@PathVariable UUID ruleId, @PathVariable UUID actionId) {
    automationRuleService.removeAction(ruleId, actionId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/api/automation-rules/{ruleId}/actions/reorder")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<AutomationActionResponse>> reorderActions(
      @PathVariable UUID ruleId, @Valid @RequestBody ReorderActionsRequest request) {
    return ResponseEntity.ok(automationRuleService.reorderActions(ruleId, request.actionIds()));
  }
}
