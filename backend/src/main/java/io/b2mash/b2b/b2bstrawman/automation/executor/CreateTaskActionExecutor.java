package io.b2mash.b2b.b2bstrawman.automation.executor;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.VariableResolver;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.AssignTo;
import io.b2mash.b2b.b2bstrawman.automation.config.CreateTaskActionConfig;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CreateTaskActionExecutor implements ActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(CreateTaskActionExecutor.class);

  private final TaskService taskService;
  private final ProjectMemberRepository projectMemberRepository;
  private final VariableResolver variableResolver;

  public CreateTaskActionExecutor(
      TaskService taskService,
      ProjectMemberRepository projectMemberRepository,
      VariableResolver variableResolver) {
    this.taskService = taskService;
    this.projectMemberRepository = projectMemberRepository;
    this.variableResolver = variableResolver;
  }

  @Override
  public ActionType supportedType() {
    return ActionType.CREATE_TASK;
  }

  @Override
  public ActionResult execute(ActionConfig config, Map<String, Map<String, Object>> context) {
    if (!(config instanceof CreateTaskActionConfig taskConfig)) {
      return new ActionFailure(
          "Invalid config type for CREATE_TASK", config.getClass().getSimpleName());
    }

    try {
      String resolvedName = variableResolver.resolve(taskConfig.taskName(), context);
      String resolvedDescription = variableResolver.resolve(taskConfig.taskDescription(), context);

      UUID projectId = VariableResolver.resolveUuid(context, "project", "id");
      if (projectId == null) {
        // Try task context for project ID
        projectId = VariableResolver.resolveUuid(context, "task", "projectId");
      }
      if (projectId == null) {
        return new ActionFailure("Cannot create task: no projectId in context", null);
      }

      UUID createdBy = resolveActorId(context);
      UUID assigneeId =
          resolveAssignee(taskConfig.assignTo(), taskConfig.specificMemberId(), projectId, context);

      // Automation-created tasks run with elevated "owner" privileges. This is intentional:
      // the automation rule itself was configured by an admin/owner, so the resulting actions
      // should not be blocked by the permission model.
      Task task =
          taskService.createTask(
              projectId,
              resolvedName,
              resolvedDescription,
              "MEDIUM",
              null,
              null,
              createdBy,
              "owner",
              null,
              null,
              assigneeId);

      log.info("Automation created task {} in project {}", task.getId(), projectId);
      return new ActionSuccess(Map.of("createdTaskId", task.getId().toString()));
    } catch (Exception e) {
      log.error("Failed to execute CREATE_TASK action: {}", e.getMessage(), e);
      return new ActionFailure("Failed to create task: " + e.getMessage(), e.toString());
    }
  }

  private UUID resolveAssignee(
      AssignTo assignTo,
      UUID specificMemberId,
      UUID projectId,
      Map<String, Map<String, Object>> context) {
    if (assignTo == null || assignTo == AssignTo.UNASSIGNED) {
      return null;
    }
    return switch (assignTo) {
      case TRIGGER_ACTOR -> resolveActorId(context);
      case PROJECT_OWNER -> {
        var leads = projectMemberRepository.findByProjectIdAndProjectRole(projectId, "LEAD");
        yield leads.isEmpty() ? null : leads.getFirst().getMemberId();
      }
      case SPECIFIC_MEMBER -> specificMemberId;
      case UNASSIGNED -> null;
    };
  }

  private UUID resolveActorId(Map<String, Map<String, Object>> context) {
    return VariableResolver.resolveUuid(context, "actor", "id");
  }
}
