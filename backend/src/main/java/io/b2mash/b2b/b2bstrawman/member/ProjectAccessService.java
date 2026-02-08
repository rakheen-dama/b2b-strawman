package io.b2mash.b2b.b2bstrawman.member;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAccessService {

  private static final String ROLE_OWNER = "owner";
  private static final String ROLE_ADMIN = "admin";
  private static final String ROLE_LEAD = "lead";

  private final ProjectMemberRepository projectMemberRepository;

  public ProjectAccessService(ProjectMemberRepository projectMemberRepository) {
    this.projectMemberRepository = projectMemberRepository;
  }

  @Transactional(readOnly = true)
  public ProjectAccess checkAccess(UUID projectId, UUID memberId, String orgRole) {
    if (ROLE_OWNER.equals(orgRole)) {
      var projectRole = lookupProjectRole(projectId, memberId);
      return new ProjectAccess(true, true, true, true, projectRole);
    }

    if (ROLE_ADMIN.equals(orgRole)) {
      var projectRole = lookupProjectRole(projectId, memberId);
      return new ProjectAccess(true, true, true, false, projectRole);
    }

    // org:member — access depends on project membership
    return projectMemberRepository
        .findByProjectIdAndMemberId(projectId, memberId)
        .map(
            pm -> {
              boolean isLead = ROLE_LEAD.equals(pm.getProjectRole());
              return new ProjectAccess(true, isLead, isLead, false, pm.getProjectRole());
            })
        .orElse(ProjectAccess.DENIED);
  }

  private String lookupProjectRole(UUID projectId, UUID memberId) {
    return projectMemberRepository
        .findByProjectIdAndMemberId(projectId, memberId)
        .map(ProjectMember::getProjectRole)
        .orElse(null);
  }
}
