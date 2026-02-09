package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAccessService {

  private final ProjectMemberRepository projectMemberRepository;

  public ProjectAccessService(ProjectMemberRepository projectMemberRepository) {
    this.projectMemberRepository = projectMemberRepository;
  }

  @Transactional(readOnly = true)
  public ProjectAccess checkAccess(UUID projectId, UUID memberId, String orgRole) {
    if (Roles.ORG_OWNER.equals(orgRole)) {
      var projectRole = lookupProjectRole(projectId, memberId);
      return new ProjectAccess(true, true, true, true, projectRole);
    }

    if (Roles.ORG_ADMIN.equals(orgRole)) {
      var projectRole = lookupProjectRole(projectId, memberId);
      return new ProjectAccess(true, true, true, false, projectRole);
    }

    // org:member — access depends on project membership
    return projectMemberRepository
        .findByProjectIdAndMemberId(projectId, memberId)
        .map(
            pm -> {
              boolean isLead = Roles.PROJECT_LEAD.equals(pm.getProjectRole());
              return new ProjectAccess(true, isLead, isLead, false, pm.getProjectRole());
            })
        .orElse(ProjectAccess.DENIED);
  }

  /**
   * Checks access and throws ResourceNotFoundException if the caller cannot view the project. This
   * provides security-by-obscurity: unauthorized users see "not found" rather than "forbidden".
   */
  @Transactional(readOnly = true)
  public ProjectAccess requireViewAccess(UUID projectId, UUID memberId, String orgRole) {
    var access = checkAccess(projectId, memberId, orgRole);
    if (!access.canView()) {
      throw new ResourceNotFoundException("Project", projectId);
    }
    return access;
  }

  /**
   * Checks access and throws ForbiddenException if the caller cannot edit the project. Non-members
   * get 404 (not 403) via requireViewAccess.
   */
  @Transactional(readOnly = true)
  public ProjectAccess requireEditAccess(UUID projectId, UUID memberId, String orgRole) {
    var access = requireViewAccess(projectId, memberId, orgRole);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot edit project", "You do not have permission to edit project " + projectId);
    }
    return access;
  }

  private String lookupProjectRole(UUID projectId, UUID memberId) {
    return projectMemberRepository
        .findByProjectIdAndMemberId(projectId, memberId)
        .map(ProjectMember::getProjectRole)
        .orElse(null);
  }
}
