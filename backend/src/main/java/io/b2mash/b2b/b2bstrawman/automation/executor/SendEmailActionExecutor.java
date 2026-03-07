package io.b2mash.b2b.b2bstrawman.automation.executor;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.VariableResolver;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.SendEmailActionConfig;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.notification.channel.EmailNotificationChannel;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SendEmailActionExecutor implements ActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(SendEmailActionExecutor.class);

  private final EmailNotificationChannel emailChannel;
  private final NotificationService notificationService;
  private final PortalContactService portalContactService;
  private final MemberRepository memberRepository;
  private final VariableResolver variableResolver;

  public SendEmailActionExecutor(
      EmailNotificationChannel emailChannel,
      NotificationService notificationService,
      PortalContactService portalContactService,
      MemberRepository memberRepository,
      VariableResolver variableResolver) {
    this.emailChannel = emailChannel;
    this.notificationService = notificationService;
    this.portalContactService = portalContactService;
    this.memberRepository = memberRepository;
    this.variableResolver = variableResolver;
  }

  @Override
  public ActionType supportedType() {
    return ActionType.SEND_EMAIL;
  }

  @Override
  public ActionResult execute(ActionConfig config, Map<String, Map<String, Object>> context) {
    if (!(config instanceof SendEmailActionConfig emailConfig)) {
      return new ActionFailure(
          "Invalid config type for SEND_EMAIL", config.getClass().getSimpleName());
    }

    try {
      String resolvedSubject = variableResolver.resolve(emailConfig.subject(), context);
      String resolvedBody = variableResolver.resolve(emailConfig.body(), context);

      UUID refEntityId = resolveEntityId(context);
      UUID refProjectId = resolveProjectId(context);

      // ALL_ADMINS: send individual emails to each admin
      if ("ALL_ADMINS".equals(emailConfig.recipientType())) {
        var admins = memberRepository.findByOrgRoleIn(List.of("admin", "owner"));
        if (admins.isEmpty()) {
          return new ActionFailure(
              "No email address resolved for recipient type: ALL_ADMINS", null);
        }
        int sent = 0;
        for (var admin : admins) {
          var notification =
              notificationService.createNotification(
                  admin.getId(),
                  "AUTOMATION_EMAIL",
                  resolvedSubject,
                  resolvedBody,
                  "AutomationRule",
                  refEntityId,
                  refProjectId);
          emailChannel.deliver(notification, admin.getEmail());
          sent++;
        }
        log.debug("Automation sent email to {} admin(s)", sent);
        return new ActionSuccess(Map.of("emailsSent", sent));
      }

      // Single-recipient path
      String recipientEmail = resolveRecipientEmail(emailConfig.recipientType(), context);
      if (recipientEmail == null || recipientEmail.isBlank()) {
        return new ActionFailure(
            "No email address resolved for recipient type: " + emailConfig.recipientType(), null);
      }

      UUID recipientMemberId = resolveRecipientMemberId(emailConfig.recipientType(), context);
      if (recipientMemberId == null) {
        return new ActionFailure(
            "No member ID resolved for recipient type: " + emailConfig.recipientType(), null);
      }

      var notification =
          notificationService.createNotification(
              recipientMemberId,
              "AUTOMATION_EMAIL",
              resolvedSubject,
              resolvedBody,
              "AutomationRule",
              refEntityId,
              refProjectId);

      emailChannel.deliver(notification, recipientEmail);

      log.debug("Automation sent email to recipient");
      return new ActionSuccess(Map.of("emailSentTo", recipientEmail));
    } catch (Exception e) {
      log.error("Failed to execute SEND_EMAIL action: {}", e.getMessage(), e);
      return new ActionFailure("Failed to send email: " + e.getMessage(), e.toString());
    }
  }

  private String resolveRecipientEmail(
      String recipientType, Map<String, Map<String, Object>> context) {
    if (recipientType == null) {
      return null;
    }
    return switch (recipientType) {
      case "TRIGGER_ACTOR" -> {
        UUID actorId = VariableResolver.resolveUuid(context, "actor", "id");
        if (actorId == null) {
          yield null;
        }
        yield memberRepository.findById(actorId).map(Member::getEmail).orElse(null);
      }
      case "CUSTOMER_CONTACT" -> {
        UUID customerId = VariableResolver.resolveUuid(context, "customer", "id");
        if (customerId == null) {
          yield null;
        }
        var contacts = portalContactService.listContactsForCustomer(customerId);
        yield contacts.isEmpty() ? null : contacts.getFirst().getEmail();
      }
      case "ALL_ADMINS" -> null; // Handled in execute() via multi-recipient path
      default -> null;
    };
  }

  private UUID resolveRecipientMemberId(
      String recipientType, Map<String, Map<String, Object>> context) {
    if (recipientType == null) {
      return null;
    }
    return switch (recipientType) {
      case "TRIGGER_ACTOR" -> VariableResolver.resolveUuid(context, "actor", "id");
      case "ALL_ADMINS" -> null; // Handled in execute() via multi-recipient path
      default -> VariableResolver.resolveUuid(context, "actor", "id"); // fallback to actor
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
    return entityId;
  }
}
