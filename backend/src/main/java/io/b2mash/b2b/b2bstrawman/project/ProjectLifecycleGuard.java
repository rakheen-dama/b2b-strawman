package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Centralized guard for project lifecycle checks. Services that create child entities (tasks, time
 * entries, documents) call this guard instead of implementing ad-hoc archive checks.
 */
@Component
public class ProjectLifecycleGuard {

  private final ProjectRepository projectRepository;

  public ProjectLifecycleGuard(ProjectRepository projectRepository) {
    this.projectRepository = projectRepository;
  }

  /**
   * Requires the project to not be read-only (archived). Throws {@link InvalidStateException} if
   * the project is archived.
   */
  public void requireNotReadOnly(UUID projectId) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    if (project.isReadOnly()) {
      throw new InvalidStateException(
          "Project is archived", "Project is archived. No modifications allowed.");
    }
  }

  /**
   * Requires the project to be in ACTIVE status. Throws {@link InvalidStateException} if the
   * project is not active.
   */
  public void requireActive(UUID projectId) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    if (project.getStatus() != ProjectStatus.ACTIVE) {
      throw new InvalidStateException(
          "Project is not active",
          "Project must be in ACTIVE status. Current status: " + project.getStatus());
    }
  }
}
