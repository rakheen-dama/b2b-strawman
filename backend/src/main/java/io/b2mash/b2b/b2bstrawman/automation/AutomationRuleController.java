package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationRuleResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.CreateRuleRequest;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.TestRuleRequest;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.TestRuleResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.UpdateRuleRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AutomationRuleController {

  private final AutomationRuleService automationRuleService;

  public AutomationRuleController(AutomationRuleService automationRuleService) {
    this.automationRuleService = automationRuleService;
  }

  @GetMapping("/api/automation-rules")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<AutomationRuleResponse>> list(
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) TriggerType triggerType) {
    return ResponseEntity.ok(automationRuleService.listRules(enabled, triggerType));
  }

  @PostMapping("/api/automation-rules")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AutomationRuleResponse> create(
      @Valid @RequestBody CreateRuleRequest request) {
    var response = automationRuleService.createRule(request);
    return ResponseEntity.created(URI.create("/api/automation-rules/" + response.id()))
        .body(response);
  }

  @GetMapping("/api/automation-rules/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AutomationRuleResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(automationRuleService.getRule(id));
  }

  @PutMapping("/api/automation-rules/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AutomationRuleResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateRuleRequest request) {
    return ResponseEntity.ok(automationRuleService.updateRule(id, request));
  }

  @DeleteMapping("/api/automation-rules/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    automationRuleService.deleteRule(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/automation-rules/{id}/toggle")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AutomationRuleResponse> toggle(@PathVariable UUID id) {
    return ResponseEntity.ok(automationRuleService.toggleRule(id));
  }

  @PostMapping("/api/automation-rules/{id}/duplicate")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AutomationRuleResponse> duplicate(@PathVariable UUID id) {
    var response = automationRuleService.duplicateRule(id);
    return ResponseEntity.created(URI.create("/api/automation-rules/" + response.id()))
        .body(response);
  }

  @PostMapping("/api/automation-rules/{id}/test")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TestRuleResponse> test(
      @PathVariable UUID id, @Valid @RequestBody TestRuleRequest request) {
    return ResponseEntity.ok(automationRuleService.testRule(id, request.sampleEventData()));
  }
}
