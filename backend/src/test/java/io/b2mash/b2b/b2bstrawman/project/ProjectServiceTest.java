package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.member.ProjectAccess;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.ErrorResponseException;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Mock private ProjectRepository repository;
  @Mock private ProjectMemberRepository projectMemberRepository;
  @Mock private ProjectAccessService projectAccessService;
  @InjectMocks private ProjectService service;

  @Test
  void listProjects_adminSeesAll() {
    var project = new Project("Test", "Desc", MEMBER_ID);
    when(repository.findAllProjectsWithRole(MEMBER_ID))
        .thenReturn(List.of(new ProjectWithRole(project, null)));

    var result = service.listProjects(MEMBER_ID, "admin");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().project().getName()).isEqualTo("Test");
    assertThat(result.getFirst().projectRole()).isNull();
    verify(repository).findAllProjectsWithRole(MEMBER_ID);
  }

  @Test
  void listProjects_memberSeesOnlyTheirProjects() {
    var project = new Project("Mine", "Desc", MEMBER_ID);
    when(repository.findProjectsForMember(MEMBER_ID))
        .thenReturn(List.of(new ProjectWithRole(project, "lead")));

    var result = service.listProjects(MEMBER_ID, "member");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().project().getName()).isEqualTo("Mine");
    verify(repository).findProjectsForMember(MEMBER_ID);
  }

  @Test
  void getProject_returnsProjectWhenAccessible() {
    var id = UUID.randomUUID();
    var project = new Project("Found", "Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(project));
    when(projectAccessService.checkAccess(id, MEMBER_ID, "member"))
        .thenReturn(new ProjectAccess(true, false, false, false, "member"));

    var result = service.getProject(id, MEMBER_ID, "member");

    assertThat(result).isPresent();
    assertThat(result.get().project().getName()).isEqualTo("Found");
    assertThat(result.get().projectRole()).isEqualTo("member");
  }

  @Test
  void getProject_returnsEmptyWhenNotFound() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    var result = service.getProject(id, MEMBER_ID, "member");

    assertThat(result).isEmpty();
  }

  @Test
  void getProject_returnsEmptyWhenAccessDenied() {
    var id = UUID.randomUUID();
    var project = new Project("Secret", "Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(project));
    when(projectAccessService.checkAccess(id, MEMBER_ID, "member"))
        .thenReturn(ProjectAccess.DENIED);

    var result = service.getProject(id, MEMBER_ID, "member");

    assertThat(result).isEmpty();
  }

  @Test
  void createProject_savesNewEntity() {
    var project = new Project("New", "Description", MEMBER_ID);
    when(repository.save(any(Project.class))).thenReturn(project);
    when(projectMemberRepository.save(any(ProjectMember.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.createProject("New", "Description", MEMBER_ID);

    assertThat(result.getName()).isEqualTo("New");
    assertThat(result.getDescription()).isEqualTo("Description");
    assertThat(result.getCreatedBy()).isEqualTo(MEMBER_ID);
    verify(repository).save(any(Project.class));
    verify(projectMemberRepository).save(any(ProjectMember.class));
  }

  @Test
  void updateProject_updatesWhenCanEdit() {
    var id = UUID.randomUUID();
    var existing = new Project("Old", "Old Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    when(repository.save(existing)).thenReturn(existing);
    when(projectAccessService.checkAccess(id, MEMBER_ID, "admin"))
        .thenReturn(new ProjectAccess(true, true, true, false, null));

    var result = service.updateProject(id, "Updated", "New Desc", MEMBER_ID, "admin");

    assertThat(result).isPresent();
    assertThat(result.get().project().getName()).isEqualTo("Updated");
    assertThat(result.get().project().getDescription()).isEqualTo("New Desc");
    verify(repository).save(existing);
  }

  @Test
  void updateProject_returnsEmptyWhenNotFound() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    var result = service.updateProject(id, "Name", "Desc", MEMBER_ID, "admin");

    assertThat(result).isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  void updateProject_throwsForbiddenWhenCannotEdit() {
    var id = UUID.randomUUID();
    var existing = new Project("No Edit", "Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    when(projectAccessService.checkAccess(id, MEMBER_ID, "member"))
        .thenReturn(new ProjectAccess(true, false, false, false, "member"));

    assertThatThrownBy(() -> service.updateProject(id, "Name", "Desc", MEMBER_ID, "member"))
        .isInstanceOf(ErrorResponseException.class);
  }

  @Test
  void updateProject_returnsEmptyWhenAccessDenied() {
    var id = UUID.randomUUID();
    var existing = new Project("Secret", "Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    when(projectAccessService.checkAccess(id, MEMBER_ID, "member"))
        .thenReturn(ProjectAccess.DENIED);

    var result = service.updateProject(id, "Name", "Desc", MEMBER_ID, "member");

    assertThat(result).isEmpty();
  }

  @Test
  void deleteProject_returnsTrueWhenDeleted() {
    var id = UUID.randomUUID();
    var project = new Project("ToDelete", null, MEMBER_ID);
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
