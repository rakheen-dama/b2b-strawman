package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberService {

  private static final Logger log = LoggerFactory.getLogger(ProjectMemberService.class);

  static final String ROLE_LEAD = "lead";
  static final String ROLE_MEMBER = "member";

  private final ProjectMemberRepository projectMemberRepository;
  private final MemberRepository memberRepository;

  public ProjectMemberService(
      ProjectMemberRepository projectMemberRepository, MemberRepository memberRepository) {
    this.projectMemberRepository = projectMemberRepository;
    this.memberRepository = memberRepository;
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

    var projectMember = new ProjectMember(projectId, memberId, ROLE_MEMBER, addedBy);
    projectMember = projectMemberRepository.save(projectMember);
    log.info("Added member {} to project {} as {}", memberId, projectId, ROLE_MEMBER);
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

    if (ROLE_LEAD.equals(projectMember.getProjectRole())) {
      throw new InvalidStateException(
          "Cannot remove project lead",
          "Transfer lead role to another member before removing the current lead");
    }

    projectMemberRepository.delete(projectMember);
    log.info("Removed member {} from project {}", memberId, projectId);
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

    if (!ROLE_LEAD.equals(currentLead.getProjectRole())) {
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

    currentLead.setProjectRole(ROLE_MEMBER);
    newLead.setProjectRole(ROLE_LEAD);

    projectMemberRepository.save(currentLead);
    projectMemberRepository.save(newLead);

    log.info("Transferred lead of project {} from {} to {}", projectId, currentLeadId, newLeadId);
  }

  @Transactional(readOnly = true)
  public boolean isProjectMember(UUID projectId, UUID memberId) {
    return projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId);
  }
}
