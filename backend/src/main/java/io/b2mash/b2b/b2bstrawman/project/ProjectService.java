package io.b2mash.b2b.b2bstrawman.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  private final ProjectRepository repository;

  public ProjectService(ProjectRepository repository) {
    this.repository = repository;
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
  public Project createProject(String name, String description, String createdBy) {
    return repository.save(new Project(name, description, createdBy));
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
