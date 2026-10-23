package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.event.MemberAddedToProjectEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberService {

  private static final Logger log = LoggerFactory.getLogger(ProjectMemberService.class);

  private final ProjectMemberRepository projectMemberRepository;
  private final MemberRepository memberRepository;
  private final MemberNameResolver memberNameResolver;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final ProjectRepository projectRepository;
  private final ProjectAccessService projectAccessService;

  public ProjectMemberService(
      ProjectMemberRepository projectMemberRepository,
      MemberRepository memberRepository,
      MemberNameResolver memberNameResolver,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      ProjectRepository projectRepository,
      ProjectAccessService projectAccessService) {
    this.projectMemberRepository = projectMemberRepository;
    this.memberRepository = memberRepository;
    this.memberNameResolver = memberNameResolver;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.projectRepository = projectRepository;
    this.projectAccessService = projectAccessService;
  }

  @Transactional(readOnly = true)
  public List<ProjectMemberInfo> listProjectMembers(UUID projectId) {
    return projectMemberRepository.findProjectMembersWithDetails(projectId);
  }

  /**
   * HTTP path: adds {@code memberId} to the matter team, gated to Owner, Admin or the matter's
   * current lead (ADR-346). The acting member is recorded as {@code addedBy}.
   */
  @Transactional
  public ProjectMember addMember(UUID projectId, UUID memberId, ActorContext actor) {
    requireCanManage(projectId, actor);
    return addMemberAsSystem(projectId, memberId, actor.memberId());
  }

  /**
   * System path for trusted internal callers only (the automation {@code
   * AssignMemberActionExecutor}, including runs from the delayed-action job, and test setup).
   * Performs NO access check — callers own authorisation, and it may run without a member scope.
   * Deliberately not an overload of {@link #addMember(UUID, UUID, ActorContext)}: a UUID from
   * {@code RequestScopes.requireMemberId()} must never silently bind to the ungated path. HTTP and
   * MCP callers must use {@link #addMember(UUID, UUID, ActorContext)}.
   */
  @Transactional
  public ProjectMember addMemberAsSystem(UUID projectId, UUID memberId, UUID addedBy) {
    if (!memberRepository.existsById(memberId)) {
      throw new ResourceNotFoundException("Member", memberId);
    }

    if (projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
      throw new ResourceConflictException(
          "Member already on project",
          "Member " + memberId + " is already a member of project " + projectId);
    }

    var projectMember = new ProjectMember(projectId, memberId, Roles.PROJECT_MEMBER, addedBy);
    projectMember = projectMemberRepository.save(projectMember);
    log.info("Added member {} to project {} as {}", memberId, projectId, Roles.PROJECT_MEMBER);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project_member.added")
            .entityType("project_member")
            .entityId(projectMember.getId())
            .details(
                Map.of(
                    "project_id", projectId.toString(),
                    "member_id", memberId.toString(),
                    "role", Roles.PROJECT_MEMBER))
            .build());

    String actorName = resolveActorName(addedBy);
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    String projectName =
        projectRepository.findById(projectId).map(p -> p.getName()).orElse("Unknown Project");
    eventPublisher.publishEvent(
        new MemberAddedToProjectEvent(
            "project_member.added",
            "project_member",
            projectMember.getId(),
            projectId,
            addedBy,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("member_id", memberId.toString(), "role", Roles.PROJECT_MEMBER),
            memberId,
            projectName));

    return projectMember;
  }

  /**
   * Removes a non-lead member from the matter team. Gated to Owner, Admin or the current lead
   * (ADR-346); removing the lead is refused (400) for every caller — transfer the lead first.
   */
  @Transactional
  public void removeMember(UUID projectId, UUID memberId, ActorContext actor) {
    requireCanManage(projectId, actor);
    var projectMember =
        projectMemberRepository
            .findByProjectIdAndMemberId(projectId, memberId)
            .orElseThrow(
                () ->
                    ResourceNotFoundException.withDetail(
                        "Project member not found",
                        "Member " + memberId + " is not a member of project " + projectId));

    if (Roles.PROJECT_LEAD.equals(projectMember.getProjectRole())) {
      throw new InvalidStateException(
          "Cannot remove project lead",
          "Transfer lead role to another member before removing the current lead");
    }

    projectMemberRepository.delete(projectMember);
    log.info("Removed member {} from project {}", memberId, projectId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project_member.removed")
            .entityType("project_member")
            .entityId(projectMember.getId())
            .details(
                Map.of(
                    "project_id", projectId.toString(),
                    "member_id", memberId.toString()))
            .build());
  }

  /**
   * Makes {@code newLeadId} the matter lead and demotes the current lead to member, in one
   * transaction. Gated to Owner, Admin or the current lead (ADR-346) — the caller need not be the
   * lead. A target who is already the lead is a no-op (no audit).
   */
  @Transactional
  public void transferLead(UUID projectId, ActorContext actor, UUID newLeadId) {
    requireCanManage(projectId, actor);
    var newLead =
        projectMemberRepository
            .findByProjectIdAndMemberId(projectId, newLeadId)
            .orElseThrow(
                () ->
                    ResourceNotFoundException.withDetail(
                        "New lead not found",
                        "Member " + newLeadId + " is not a member of project " + projectId));

    if (Roles.PROJECT_LEAD.equals(newLead.getProjectRole())) {
      return;
    }

    // Demote first and flush: idx_project_members_unique_lead is not deferrable, and the gate may
    // have loaded the target's row into the persistence context before the current lead's, so
    // without the flush Hibernate could issue the promote UPDATE first.
    for (var currentLead :
        projectMemberRepository.findByProjectIdAndProjectRole(projectId, Roles.PROJECT_LEAD)) {
      currentLead.setProjectRole(Roles.PROJECT_MEMBER);
      projectMemberRepository.saveAndFlush(currentLead);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("project_member.role_changed")
              .entityType("project_member")
              .entityId(currentLead.getId())
              .details(
                  Map.of(
                      "role", Map.of("from", "lead", "to", "member"),
                      "project_id", projectId.toString(),
                      "member_id", currentLead.getMemberId().toString()))
              .build());
    }

    newLead.setProjectRole(Roles.PROJECT_LEAD);
    projectMemberRepository.save(newLead);

    log.info(
        "Transferred lead of project {} to {} (by {})", projectId, newLeadId, actor.memberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project_member.role_changed")
            .entityType("project_member")
            .entityId(newLead.getId())
            .details(
                Map.of(
                    "role", Map.of("from", "member", "to", "lead"),
                    "project_id", projectId.toString(),
                    "member_id", newLeadId.toString()))
            .build());
  }

  @Transactional(readOnly = true)
  public boolean isProjectMember(UUID projectId, UUID memberId) {
    return projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId);
  }

  /**
   * Owner, Admin, or the matter's current lead (ADR-346). A caller who cannot view the matter (an
   * org Member not on it) gets 404 from requireViewAccess first; a Member on the matter who is not
   * the lead gets 403.
   */
  private void requireCanManage(UUID projectId, ActorContext actor) {
    var access = projectAccessService.requireViewAccess(projectId, actor);
    if (!access.canManageMembers()) {
      throw new ForbiddenException(
          "Cannot manage matter team", "Only the matter lead or a firm admin can manage the team");
    }
  }

  private String resolveActorName(UUID memberId) {
    return memberNameResolver.resolveName(memberId);
  }
}
