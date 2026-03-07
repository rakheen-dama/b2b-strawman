package io.b2mash.b2b.b2bstrawman.automation.executor;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.VariableResolver;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.SendNotificationActionConfig;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SendNotificationActionExecutor implements ActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(SendNotificationActionExecutor.class);

  private final NotificationService notificationService;
  private final MemberRepository memberRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final VariableResolver variableResolver;

  public SendNotificationActionExecutor(
      NotificationService notificationService,
      MemberRepository memberRepository,
      ProjectMemberRepository projectMemberRepository,
      VariableResolver variableResolver) {
    this.notificationService = notificationService;
    this.memberRepository = memberRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.variableResolver = variableResolver;
  }

  @Override
  public ActionType supportedType() {
    return ActionType.SEND_NOTIFICATION;
  }

  @Override
  public ActionResult execute(ActionConfig config, Map<String, Map<String, Object>> context) {
    if (!(config instanceof SendNotificationActionConfig notifConfig)) {
      return new ActionFailure(
          "Invalid config type for SEND_NOTIFICATION", config.getClass().getSimpleName());
    }

    try {
      String resolvedTitle = variableResolver.resolve(notifConfig.title(), context);
      String resolvedMessage = variableResolver.resolve(notifConfig.message(), context);

      List<UUID> recipientIds = resolveRecipients(notifConfig.recipientType(), context);
      if (recipientIds.isEmpty()) {
        return new ActionFailure(
            "No recipients resolved for type: " + notifConfig.recipientType(), null);
      }

      UUID refEntityId = resolveEntityId(context);
      UUID refProjectId = resolveProjectId(context);
      int sent = 0;

      for (UUID recipientId : recipientIds) {
        notificationService.createNotification(
            recipientId,
            "AUTOMATION",
            resolvedTitle,
            resolvedMessage,
            "AutomationRule",
            refEntityId,
            refProjectId);
        sent++;
      }

      log.info("Automation sent {} notification(s)", sent);
      return new ActionSuccess(Map.of("notificationsSent", sent));
    } catch (Exception e) {
      log.error("Failed to execute SEND_NOTIFICATION action: {}", e.getMessage(), e);
      return new ActionFailure("Failed to send notification: " + e.getMessage(), e.toString());
    }
  }

  private List<UUID> resolveRecipients(
      String recipientType, Map<String, Map<String, Object>> context) {
    if (recipientType == null) {
      return List.of();
    }
    return switch (recipientType) {
      case "TRIGGER_ACTOR" -> {
        UUID actorId = VariableResolver.resolveUuid(context, "actor", "id");
        yield actorId != null ? List.of(actorId) : List.of();
      }
      case "PROJECT_OWNER" -> {
        UUID projectId = resolveProjectId(context);
        if (projectId == null) {
          yield List.of();
        }
        var leads = projectMemberRepository.findByProjectIdAndProjectRole(projectId, "LEAD");
        yield leads.isEmpty() ? List.of() : List.of(leads.getFirst().getMemberId());
      }
      case "PROJECT_MEMBERS" -> {
        UUID projectId = resolveProjectId(context);
        if (projectId == null) {
          yield List.of();
        }
        yield projectMemberRepository.findByProjectId(projectId).stream()
            .map(m -> m.getMemberId())
            .toList();
      }
      case "ALL_ADMINS" -> {
        yield memberRepository.findByOrgRoleIn(List.of("admin", "owner")).stream()
            .map(m -> m.getId())
            .toList();
      }
      case "SPECIFIC_MEMBER" -> {
        // SPECIFIC_MEMBER requires a specificMemberId field in the config, which is not
        // currently supported by SendNotificationActionConfig. Return empty to trigger
        // the "No recipients resolved" failure path with a clear error.
        log.warn(
            "SPECIFIC_MEMBER recipient type is not supported for SEND_NOTIFICATION — "
                + "SendNotificationActionConfig does not have a specificMemberId field");
        yield List.of();
      }
      default -> List.of();
    };
  }

  private UUID resolveProjectId(Map<String, Map<String, Object>> context) {
    UUID projectId = VariableResolver.resolveUuid(context, "project", "id");
    if (projectId == null) {
      projectId = VariableResolver.resolveUuid(context, "task", "projectId");
    }
    return projectId;
  }

  private UUID resolveEntityId(Map<String, Map<String, Object>> context) {
    UUID entityId = VariableResolver.resolveUuid(context, "task", "id");
    if (entityId == null) {
      entityId = VariableResolver.resolveUuid(context, "project", "id");
    }
    if (entityId == null) {
      entityId = VariableResolver.resolveUuid(context, "rule", "id");
    }
    return entityId;
  }
}
