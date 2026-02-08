package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import java.util.List;
import java.util.Optional;
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

  public ProjectService(
      ProjectRepository repository, ProjectMemberRepository projectMemberRepository) {
    this.repository = repository;
    this.projectMemberRepository = projectMemberRepository;
  }

  @Transactional(readOnly = true)
  public List<Project> listProjects() {
    return repository.findAll();
  }

  @Transactional(readOnly = true)
  public Optional<Project> getProject(UUID id) {
    return repository.findById(id);
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
  public Optional<Project> updateProject(UUID id, String name, String description) {
    return repository
        .findById(id)
        .map(
            project -> {
              project.update(name, description);
              return repository.save(project);
            });
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
}
