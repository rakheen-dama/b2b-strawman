package io.b2mash.b2b.b2bstrawman.automation.executor;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.VariableResolver;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.AssignMemberActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.AssignTo;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AssignMemberActionExecutor implements ActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(AssignMemberActionExecutor.class);

  private final ProjectMemberService projectMemberService;
  private final ProjectMemberRepository projectMemberRepository;

  public AssignMemberActionExecutor(
      ProjectMemberService projectMemberService, ProjectMemberRepository projectMemberRepository) {
    this.projectMemberService = projectMemberService;
    this.projectMemberRepository = projectMemberRepository;
  }

  @Override
  public ActionType supportedType() {
    return ActionType.ASSIGN_MEMBER;
  }

  @Override
  public ActionResult execute(ActionConfig config, Map<String, Map<String, Object>> context) {
    if (!(config instanceof AssignMemberActionConfig assignConfig)) {
      return new ActionFailure(
          "Invalid config type for ASSIGN_MEMBER", config.getClass().getSimpleName());
    }

    try {
      UUID projectId = VariableResolver.resolveUuid(context, "project", "id");
      if (projectId == null) {
        projectId = VariableResolver.resolveUuid(context, "task", "projectId");
      }
      if (projectId == null) {
        return new ActionFailure("Cannot assign member: no projectId in context", null);
      }

      UUID memberId =
          resolveAssignee(
              assignConfig.assignTo(), assignConfig.specificMemberId(), projectId, context);
      if (memberId == null) {
        return new ActionFailure(
            "Cannot assign member: could not resolve member for assignTo="
                + assignConfig.assignTo(),
            null);
      }

      UUID actorId = resolveActorId(context);
      projectMemberService.addMember(projectId, memberId, actorId);

      log.info("Automation assigned member {} to project {}", memberId, projectId);
      return new ActionSuccess(Map.of("assignedMemberId", memberId.toString()));
    } catch (Exception e) {
      log.error("Failed to execute ASSIGN_MEMBER action: {}", e.getMessage(), e);
      return new ActionFailure("Failed to assign member: " + e.getMessage(), e.toString());
    }
  }

  private UUID resolveAssignee(
      AssignTo assignTo,
      UUID specificMemberId,
      UUID projectId,
      Map<String, Map<String, Object>> context) {
    if (assignTo == null) {
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
