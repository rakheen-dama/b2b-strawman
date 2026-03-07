package io.b2mash.b2b.b2bstrawman.automation.template;

import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationRuleResponse;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.TemplateDefinitionResponse;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AutomationTemplateController {

  private final AutomationTemplateService automationTemplateService;

  public AutomationTemplateController(AutomationTemplateService automationTemplateService) {
    this.automationTemplateService = automationTemplateService;
  }

  @GetMapping("/api/automation-templates")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TemplateDefinitionResponse>> listTemplates() {
    return ResponseEntity.ok(automationTemplateService.listTemplates());
  }

  @PostMapping("/api/automation-templates/{slug}/activate")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AutomationRuleResponse> activateTemplate(@PathVariable String slug) {
    var response = automationTemplateService.activateTemplate(slug);
    return ResponseEntity.created(URI.create("/api/automation-rules/" + response.id()))
        .body(response);
  }
}
