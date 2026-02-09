package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

  private final ProjectRepository repository;
  private final ProjectMemberRepository projectMemberRepository;
  private final ProjectAccessService projectAccessService;

  public ProjectService(
      ProjectRepository repository,
      ProjectMemberRepository projectMemberRepository,
      ProjectAccessService projectAccessService) {
    this.repository = repository;
    this.projectMemberRepository = projectMemberRepository;
    this.projectAccessService = projectAccessService;
  }

  @Transactional(readOnly = true)
  public List<ProjectWithRole> listProjects(UUID memberId, String orgRole) {
    if ("owner".equals(orgRole) || "admin".equals(orgRole)) {
      return repository.findAllProjectsWithRole(memberId);
    }
    return repository.findProjectsForMember(memberId);
  }

  @Transactional(readOnly = true)
  public ProjectWithRole getProject(UUID id, UUID memberId, String orgRole) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    var access = projectAccessService.checkAccess(id, memberId, orgRole);
    if (!access.canView()) {
      throw new ResourceNotFoundException("Project", id);
    }
    return new ProjectWithRole(project, access.projectRole());
  }

  @Transactional
  public Project createProject(String name, String description, UUID createdBy) {
    var project = repository.save(new Project(name, description, createdBy));
    var lead = new ProjectMember(project.getId(), createdBy, "lead", null);
    projectMemberRepository.save(lead);
    log.info("Created project {} with lead member {}", project.getId(), createdBy);
    return project;
  }

  @Transactional
  public ProjectWithRole updateProject(
      UUID id, String name, String description, UUID memberId, String orgRole) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    var access = projectAccessService.checkAccess(id, memberId, orgRole);
    if (!access.canView()) {
      throw new ResourceNotFoundException("Project", id);
    }
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot edit project", "You do not have permission to edit project " + id);
    }
    project.update(name, description);
    project = repository.save(project);
    return new ProjectWithRole(project, access.projectRole());
  }

  @Transactional
  public void deleteProject(UUID id) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    repository.delete(project);
  }
}
