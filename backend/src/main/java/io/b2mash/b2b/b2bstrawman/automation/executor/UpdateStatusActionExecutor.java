package io.b2mash.b2b.b2bstrawman.automation.executor;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.VariableResolver;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.UpdateStatusActionConfig;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.task.TaskStatus;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UpdateStatusActionExecutor implements ActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(UpdateStatusActionExecutor.class);

  /**
   * Automation actions execute with elevated (owner) privileges because they are system-initiated,
   * not user-initiated. The actor ID from context identifies who triggered the event, but the role
   * is always elevated to ensure automation rules can perform any action regardless of the
   * triggering user's role.
   */
  private static final String SYSTEM_ACTOR_ROLE = "owner";

  private final TaskService taskService;
  private final TaskRepository taskRepository;
  private final ProjectService projectService;
  private final InvoiceService invoiceService;

  public UpdateStatusActionExecutor(
      TaskService taskService,
      TaskRepository taskRepository,
      ProjectService projectService,
      InvoiceService invoiceService) {
    this.taskService = taskService;
    this.taskRepository = taskRepository;
    this.projectService = projectService;
    this.invoiceService = invoiceService;
  }

  @Override
  public ActionType supportedType() {
    return ActionType.UPDATE_STATUS;
  }

  @Override
  public ActionResult execute(
      ActionConfig config, Map<String, Map<String, Object>> context, UUID automationExecutionId) {
    if (!(config instanceof UpdateStatusActionConfig statusConfig)) {
      return new ActionFailure(
          "Invalid config type for UPDATE_STATUS", config.getClass().getSimpleName());
    }

    try {
      if (statusConfig.targetEntityType() == null) {
        return new ActionFailure("Missing targetEntityType in UPDATE_STATUS config", null);
      }
      return switch (statusConfig.targetEntityType()) {
        case "TASK" -> updateTaskStatus(statusConfig.newStatus(), context);
        case "PROJECT" -> updateProjectStatus(statusConfig.newStatus(), context);
        case "CUSTOMER" -> new ActionFailure("Customer status update not yet supported", null);
        case "INVOICE" -> updateInvoiceStatus(statusConfig.newStatus(), context);
        default ->
            new ActionFailure(
                "Unknown target entity type: " + statusConfig.targetEntityType(), null);
      };
    } catch (Exception e) {
      log.error("Failed to execute UPDATE_STATUS action: {}", e.getMessage(), e);
      return new ActionFailure("Failed to update status: " + e.getMessage(), e.toString());
    }
  }

  private ActionResult updateTaskStatus(
      String newStatus, Map<String, Map<String, Object>> context) {
    UUID taskId = VariableResolver.resolveUuid(context, "task", "id");
    if (taskId == null) {
      return new ActionFailure("Cannot update task status: no task.id in context", null);
    }

    // Validate the target status is a valid TaskStatus
    TaskStatus targetStatus;
    try {
      targetStatus = TaskStatus.valueOf(newStatus);
    } catch (IllegalArgumentException e) {
      return new ActionFailure("Invalid task status: " + newStatus, null);
    }

    // Check current status and validate transition
    String currentStatusStr = resolveStringField(context, "task", "status");
    if (currentStatusStr != null) {
      try {
        TaskStatus currentStatus = TaskStatus.valueOf(currentStatusStr);
        if (!currentStatus.canTransitionTo(targetStatus)) {
          return new ActionFailure(
              "Invalid task status transition: " + currentStatus + " -> " + targetStatus, null);
        }
      } catch (IllegalArgumentException ignored) {
        // Current status unknown — let the service handle validation
      }
    }

    // Fetch current task to pass through existing field values (TaskService requires all fields)
    Task existingTask = taskRepository.findById(taskId).orElse(null);
    if (existingTask == null) {
      return new ActionFailure("Task not found: " + taskId, null);
    }

    UUID actorId = resolveActorId(context);
    if (actorId == null) {
      return new ActionFailure("No actor ID available in automation context", null);
    }

    // TODO: Add TaskService.updateTaskStatus() to avoid direct repo access
    var actorCtx = new ActorContext(actorId, SYSTEM_ACTOR_ROLE);
    taskService.updateTask(
        taskId,
        existingTask.getTitle(),
        existingTask.getDescription(),
        existingTask.getPriority() != null ? existingTask.getPriority().name() : "MEDIUM",
        newStatus,
        existingTask.getType(),
        existingTask.getDueDate(),
        existingTask.getAssigneeId(),
        actorCtx);

    log.info("Automation updated task {} status to {}", taskId, newStatus);
    return new ActionSuccess(Map.of("updatedEntityId", taskId.toString()));
  }

  private ActionResult updateProjectStatus(
      String newStatus, Map<String, Map<String, Object>> context) {
    UUID projectId = VariableResolver.resolveUuid(context, "project", "id");
    if (projectId == null) {
      return new ActionFailure("Cannot update project status: no project.id in context", null);
    }

    UUID actorId = resolveActorId(context);
    if (actorId == null) {
      return new ActionFailure("No actor ID available in automation context", null);
    }

    var actorCtx = new ActorContext(actorId, SYSTEM_ACTOR_ROLE);
    return switch (newStatus) {
      case "COMPLETED" -> {
        projectService.completeProject(projectId, true, actorCtx);
        log.info("Automation completed project {}", projectId);
        yield new ActionSuccess(Map.of("updatedEntityId", projectId.toString()));
      }
      case "ARCHIVED" -> {
        projectService.archiveProject(projectId, actorCtx);
        log.info("Automation archived project {}", projectId);
        yield new ActionSuccess(Map.of("updatedEntityId", projectId.toString()));
      }
      case "ACTIVE" -> {
        projectService.reopenProject(projectId, actorCtx);
        log.info("Automation reopened project {}", projectId);
        yield new ActionSuccess(Map.of("updatedEntityId", projectId.toString()));
      }
      default -> new ActionFailure("Unsupported project status: " + newStatus, null);
    };
  }

  private ActionResult updateInvoiceStatus(
      String newStatus, Map<String, Map<String, Object>> context) {
    UUID invoiceId = VariableResolver.resolveUuid(context, "invoice", "id");
    if (invoiceId == null) {
      return new ActionFailure("Cannot update invoice status: no invoice.id in context", null);
    }

    return switch (newStatus) {
      case "SENT" -> {
        invoiceService.send(invoiceId, null);
        log.info("Automation sent invoice {}", invoiceId);
        yield new ActionSuccess(Map.of("updatedEntityId", invoiceId.toString()));
      }
      case "PAID" -> {
        invoiceService.recordPayment(invoiceId, "automation");
        log.info("Automation recorded payment for invoice {}", invoiceId);
        yield new ActionSuccess(Map.of("updatedEntityId", invoiceId.toString()));
      }
      case "VOID" -> {
        invoiceService.voidInvoice(invoiceId);
        log.info("Automation voided invoice {}", invoiceId);
        yield new ActionSuccess(Map.of("updatedEntityId", invoiceId.toString()));
      }
      default -> new ActionFailure("Unsupported invoice status: " + newStatus, null);
    };
  }

  private UUID resolveActorId(Map<String, Map<String, Object>> context) {
    return VariableResolver.resolveUuid(context, "actor", "id");
  }

  private String resolveStringField(
      Map<String, Map<String, Object>> context, String entityKey, String fieldKey) {
    Map<String, Object> entityMap = context.get(entityKey);
    if (entityMap == null) {
      return null;
    }
    Object value = entityMap.get(fieldKey);
    return value != null ? value.toString() : null;
  }
}
