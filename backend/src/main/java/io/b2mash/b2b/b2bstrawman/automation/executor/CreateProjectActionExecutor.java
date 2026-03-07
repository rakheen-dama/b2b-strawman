package io.b2mash.b2b.b2bstrawman.automation.executor;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.VariableResolver;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.CreateProjectActionConfig;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CreateProjectActionExecutor implements ActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(CreateProjectActionExecutor.class);

  private final ProjectTemplateService projectTemplateService;
  private final VariableResolver variableResolver;

  public CreateProjectActionExecutor(
      ProjectTemplateService projectTemplateService, VariableResolver variableResolver) {
    this.projectTemplateService = projectTemplateService;
    this.variableResolver = variableResolver;
  }

  @Override
  public ActionType supportedType() {
    return ActionType.CREATE_PROJECT;
  }

  @Override
  public ActionResult execute(ActionConfig config, Map<String, Map<String, Object>> context) {
    if (!(config instanceof CreateProjectActionConfig projectConfig)) {
      return new ActionFailure(
          "Invalid config type for CREATE_PROJECT", config.getClass().getSimpleName());
    }

    try {
      String resolvedName = variableResolver.resolve(projectConfig.projectName(), context);
      UUID actorId = resolveActorId(context);

      UUID customerId = null;
      if (projectConfig.linkToCustomer()) {
        customerId = VariableResolver.resolveUuid(context, "customer", "id");
      }

      var request = new InstantiateTemplateRequest(resolvedName, customerId, null, null);
      var project =
          projectTemplateService.instantiateTemplate(
              projectConfig.projectTemplateId(), request, actorId);

      log.info(
          "Automation created project {} from template {}",
          project.getId(),
          projectConfig.projectTemplateId());
      return new ActionSuccess(Map.of("createdProjectId", project.getId().toString()));
    } catch (Exception e) {
      log.error("Failed to execute CREATE_PROJECT action: {}", e.getMessage(), e);
      return new ActionFailure("Failed to create project: " + e.getMessage(), e.toString());
    }
  }

  private UUID resolveActorId(Map<String, Map<String, Object>> context) {
    return VariableResolver.resolveUuid(context, "actor", "id");
  }
}
