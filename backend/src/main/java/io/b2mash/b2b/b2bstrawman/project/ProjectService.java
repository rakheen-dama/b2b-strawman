package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

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
  public Optional<ProjectWithRole> getProject(UUID id, UUID memberId, String orgRole) {
    var project = repository.findById(id);
    if (project.isEmpty()) {
      return Optional.empty();
    }
    var access = projectAccessService.checkAccess(id, memberId, orgRole);
    if (!access.canView()) {
      return Optional.empty();
    }
    return Optional.of(new ProjectWithRole(project.get(), access.projectRole()));
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
  public Optional<ProjectWithRole> updateProject(
      UUID id, String name, String description, UUID memberId, String orgRole) {
    var project = repository.findById(id);
    if (project.isEmpty()) {
      return Optional.empty();
    }
    var access = projectAccessService.checkAccess(id, memberId, orgRole);
    if (!access.canView()) {
      return Optional.empty();
    }
    if (!access.canEdit()) {
      throw forbidden("Cannot edit project", "You do not have permission to edit project " + id);
    }
    var updated = project.get();
    updated.update(name, description);
    updated = repository.save(updated);
    return Optional.of(new ProjectWithRole(updated, access.projectRole()));
  }

  @Transactional
  public boolean deleteProject(UUID id) {
    return repository
        .findById(id)
        .map(
            project -> {
              repository.delete(project);
              return true;
            })
        .orElse(false);
  }

  private ErrorResponseException forbidden(String title, String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setTitle(title);
    problem.setDetail(detail);
    return new ErrorResponseException(HttpStatus.FORBIDDEN, problem, null);
  }
}
