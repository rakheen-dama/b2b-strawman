package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  @Mock private ProjectRepository repository;
  @InjectMocks private ProjectService service;

  @Test
  void listProjects_delegatesToRepository() {
    var project = new Project("Test", "Desc", "user_1");
    when(repository.findAll()).thenReturn(List.of(project));

    var result = service.listProjects();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getName()).isEqualTo("Test");
    verify(repository).findAll();
  }

  @Test
  void getProject_returnsProjectWhenFound() {
    var id = UUID.randomUUID();
    var project = new Project("Found", "Desc", "user_1");
    when(repository.findById(id)).thenReturn(Optional.of(project));

    var result = service.getProject(id);

    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("Found");
  }

  @Test
  void getProject_returnsEmptyWhenNotFound() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    var result = service.getProject(id);

    assertThat(result).isEmpty();
  }

  @Test
  void createProject_savesNewEntity() {
    var project = new Project("New", "Description", "user_1");
    when(repository.save(any(Project.class))).thenReturn(project);

    var result = service.createProject("New", "Description", "user_1");

    assertThat(result.getName()).isEqualTo("New");
    assertThat(result.getDescription()).isEqualTo("Description");
    assertThat(result.getCreatedBy()).isEqualTo("user_1");
    verify(repository).save(any(Project.class));
  }

  @Test
  void updateProject_updatesExistingProject() {
    var id = UUID.randomUUID();
    var existing = new Project("Old", "Old Desc", "user_1");
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    when(repository.save(existing)).thenReturn(existing);

    var result = service.updateProject(id, "Updated", "New Desc");

    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("Updated");
    assertThat(result.get().getDescription()).isEqualTo("New Desc");
    verify(repository).save(existing);
  }

  @Test
  void updateProject_returnsEmptyWhenNotFound() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    var result = service.updateProject(id, "Name", "Desc");

    assertThat(result).isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  void deleteProject_returnsTrueWhenDeleted() {
    var id = UUID.randomUUID();
    var project = new Project("ToDelete", null, "user_1");
    when(repository.findById(id)).thenReturn(Optional.of(project));

    var result = service.deleteProject(id);

    assertThat(result).isTrue();
    verify(repository).delete(project);
  }

  @Test
  void deleteProject_returnsFalseWhenNotFound() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    var result = service.deleteProject(id);

    assertThat(result).isFalse();
    verify(repository, never()).delete(any());
  }
}
