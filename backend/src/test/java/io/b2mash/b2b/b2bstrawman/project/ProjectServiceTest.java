package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccess;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Mock private ProjectRepository repository;
  @Mock private ProjectMemberRepository projectMemberRepository;
  @Mock private ProjectAccessService projectAccessService;
  @Mock private AuditService auditService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private MemberNameResolver memberNameResolver;
  @Mock private TaskRepository taskRepository;
  @Mock private TimeEntryRepository timeEntryRepository;
  @Mock private ProjectFieldService projectFieldService;
  @Mock private ProjectDeletionGuard projectDeletionGuard;
  @InjectMocks private ProjectService service;

  @Test
  void listProjects_adminSeesAll() {
    var project = new Project("Test", "Desc", MEMBER_ID);
    when(repository.findAllProjectsWithRole(MEMBER_ID))
        .thenReturn(List.of(new ProjectWithRole(project, null)));

    var result = service.listProjects(new ActorContext(MEMBER_ID, "admin"));

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

    var result = service.listProjects(new ActorContext(MEMBER_ID, "member"));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().project().getName()).isEqualTo("Mine");
    verify(repository).findProjectsForMember(MEMBER_ID);
  }

  @Test
  void getProject_returnsProjectWhenAccessible() {
    var id = UUID.randomUUID();
    var project = new Project("Found", "Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(project));
    when(projectAccessService.requireViewAccess(id, new ActorContext(MEMBER_ID, "member")))
        .thenReturn(new ProjectAccess(true, false, false, false, "member"));

    var result = service.getProject(id, new ActorContext(MEMBER_ID, "member"));

    assertThat(result.project().getName()).isEqualTo("Found");
    assertThat(result.projectRole()).isEqualTo("member");
  }

  @Test
  void getProject_throwsWhenNotFound() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getProject(id, new ActorContext(MEMBER_ID, "member")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void getProject_throwsWhenAccessDenied() {
    var id = UUID.randomUUID();
    var project = new Project("Secret", "Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(project));
    when(projectAccessService.requireViewAccess(id, new ActorContext(MEMBER_ID, "member")))
        .thenThrow(new ResourceNotFoundException("Project", id));

    assertThatThrownBy(() -> service.getProject(id, new ActorContext(MEMBER_ID, "member")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void createProject_savesNewEntity() {
    var project = projectWithId("New", "Description", MEMBER_ID);
    when(repository.save(any(Project.class))).thenReturn(project);
    when(projectMemberRepository.save(any(ProjectMember.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(projectFieldService.prepareForCreate(eq("New"), isNull(), isNull(), isNull()))
        .thenReturn(new ProjectFieldService.CreateFieldResult(Map.of(), "New", List.of()));

    var result = service.createProject("New", "Description", MEMBER_ID);

    assertThat(result.getName()).isEqualTo("New");
    assertThat(result.getDescription()).isEqualTo("Description");
    assertThat(result.getCreatedBy()).isEqualTo(MEMBER_ID);
    verify(repository, atLeast(1)).save(any(Project.class));
    verify(projectMemberRepository).save(any(ProjectMember.class));
  }

  @Test
  void updateProject_updatesWhenCanEdit() {
    var id = UUID.randomUUID();
    var existing = projectWithId(id, "Old", "Old Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    when(repository.save(existing)).thenReturn(existing);
    when(projectAccessService.requireEditAccess(id, new ActorContext(MEMBER_ID, "admin")))
        .thenReturn(new ProjectAccess(true, true, true, false, null));

    var result =
        service.updateProject(id, "Updated", "New Desc", new ActorContext(MEMBER_ID, "admin"));

    assertThat(result.project().getName()).isEqualTo("Updated");
    assertThat(result.project().getDescription()).isEqualTo("New Desc");
    verify(repository).save(existing);
  }

  @Test
  void updateProject_throwsWhenNotFound() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.updateProject(id, "Name", "Desc", new ActorContext(MEMBER_ID, "admin")))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void updateProject_throwsForbiddenWhenCannotEdit() {
    var id = UUID.randomUUID();
    var existing = new Project("No Edit", "Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    when(projectAccessService.requireEditAccess(id, new ActorContext(MEMBER_ID, "member")))
        .thenThrow(
            new ForbiddenException(
                "Cannot edit project", "You do not have permission to edit project " + id));

    assertThatThrownBy(
            () -> service.updateProject(id, "Name", "Desc", new ActorContext(MEMBER_ID, "member")))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void updateProject_throwsWhenAccessDenied() {
    var id = UUID.randomUUID();
    var existing = new Project("Secret", "Desc", MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    when(projectAccessService.requireEditAccess(id, new ActorContext(MEMBER_ID, "member")))
        .thenThrow(new ResourceNotFoundException("Project", id));

    assertThatThrownBy(
            () -> service.updateProject(id, "Name", "Desc", new ActorContext(MEMBER_ID, "member")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void deleteProject_deletesWhenAllGuardsPass() {
    var id = UUID.randomUUID();
    var project = projectWithId(id, "ToDelete", null, MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(project));
    // ProjectDeletionGuard.checkAndExecute() is void — default Mockito behavior is do-nothing

    service.deleteProject(id);

    verify(projectDeletionGuard).checkAndExecute(id);
    verify(repository).delete(project);
  }

  @Test
  void deleteProject_throwsWhenNotFound() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteProject(id))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(repository, never()).delete(any());
  }

  @Test
  void deleteProject_rejectsCompletedProject() {
    var id = UUID.randomUUID();
    var project = projectWithId(id, "Completed", null, MEMBER_ID);
    setProjectStatus(project, ProjectStatus.COMPLETED);
    when(repository.findById(id)).thenReturn(Optional.of(project));

    assertThatThrownBy(() -> service.deleteProject(id))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("completed");
    verify(repository, never()).delete(any());
  }

  @Test
  void deleteProject_rejectsArchivedProject() {
    var id = UUID.randomUUID();
    var project = projectWithId(id, "Archived", null, MEMBER_ID);
    setProjectStatus(project, ProjectStatus.ARCHIVED);
    when(repository.findById(id)).thenReturn(Optional.of(project));

    assertThatThrownBy(() -> service.deleteProject(id))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("archived");
    verify(repository, never()).delete(any());
  }

  @Test
  void deleteProject_rejectsWhenGuardFails() {
    var id = UUID.randomUUID();
    var project = projectWithId(id, "HasTasks", null, MEMBER_ID);
    when(repository.findById(id)).thenReturn(Optional.of(project));
    doThrow(
            new ResourceConflictException(
                "Cannot delete project",
                "Cannot delete project with 3 task(s). Delete or cancel all tasks before deleting"
                    + " the project."))
        .when(projectDeletionGuard)
        .checkAndExecute(id);

    assertThatThrownBy(() -> service.deleteProject(id))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("task");
    verify(repository, never()).delete(any());
  }

  /** Creates a Project with a random ID set via reflection (JPA @GeneratedValue has no setter). */
  private static Project projectWithId(String name, String description, UUID createdBy) {
    return projectWithId(UUID.randomUUID(), name, description, createdBy);
  }

  private static Project projectWithId(UUID id, String name, String description, UUID createdBy) {
    return TestIds.withId(new Project(name, description, createdBy), id);
  }

  private static void setProjectStatus(Project project, ProjectStatus status) {
    TestIds.withField(project, "status", status);
  }
}
