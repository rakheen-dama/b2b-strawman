package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberService {

  private static final Logger log = LoggerFactory.getLogger(ProjectMemberService.class);

  private final ProjectMemberRepository projectMemberRepository;
  private final MemberRepository memberRepository;
  private final AuditService auditService;

  public ProjectMemberService(
      ProjectMemberRepository projectMemberRepository,
      MemberRepository memberRepository,
      AuditService auditService) {
    this.projectMemberRepository = projectMemberRepository;
    this.memberRepository = memberRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<ProjectMemberInfo> listProjectMembers(UUID projectId) {
    return projectMemberRepository.findProjectMembersWithDetails(projectId);
  }

  @Transactional
  public ProjectMember addMember(UUID projectId, UUID memberId, UUID addedBy) {
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

    return projectMember;
  }

  @Transactional
  public void removeMember(UUID projectId, UUID memberId, UUID requestedBy, String orgRole) {
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

  @Transactional
  public void transferLead(UUID projectId, UUID currentLeadId, UUID newLeadId) {
    var currentLead =
        projectMemberRepository
            .findByProjectIdAndMemberId(projectId, currentLeadId)
            .orElseThrow(
                () ->
                    ResourceNotFoundException.withDetail(
                        "Current lead not found",
                        "Member " + currentLeadId + " is not a member of project " + projectId));

    if (!Roles.PROJECT_LEAD.equals(currentLead.getProjectRole())) {
      throw new InvalidStateException(
          "Not the project lead",
          "Member " + currentLeadId + " is not the lead of project " + projectId);
    }

    var newLead =
        projectMemberRepository
            .findByProjectIdAndMemberId(projectId, newLeadId)
            .orElseThrow(
                () ->
                    ResourceNotFoundException.withDetail(
                        "New lead not found",
                        "Member " + newLeadId + " is not a member of project " + projectId));

    currentLead.setProjectRole(Roles.PROJECT_MEMBER);
    newLead.setProjectRole(Roles.PROJECT_LEAD);

    projectMemberRepository.save(currentLead);
    projectMemberRepository.save(newLead);

    log.info("Transferred lead of project {} from {} to {}", projectId, currentLeadId, newLeadId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project_member.role_changed")
            .entityType("project_member")
            .entityId(currentLead.getId())
            .details(
                Map.of(
                    "role", Map.of("from", "lead", "to", "member"),
                    "project_id", projectId.toString(),
                    "member_id", currentLeadId.toString()))
            .build());

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
}
