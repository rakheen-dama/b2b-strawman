package io.b2mash.b2b.b2bstrawman.task;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMemberRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupService;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccess;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final UUID PROJECT_ID = UUID.randomUUID();

  @Mock private TaskRepository taskRepository;
  @Mock private ProjectAccessService projectAccessService;
  @Mock private ProjectMemberRepository projectMemberRepository;
  @Mock private AuditService auditService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private MemberNameResolver memberNameResolver;
  @Mock private CustomFieldValidator customFieldValidator;
  @Mock private FieldGroupRepository fieldGroupRepository;
  @Mock private FieldGroupMemberRepository fieldGroupMemberRepository;
  @Mock private FieldDefinitionRepository fieldDefinitionRepository;
  @Mock private CustomerProjectRepository customerProjectRepository;
  @Mock private FieldGroupService fieldGroupService;
  @Mock private ProjectRepository projectRepository;
  @Mock private TimeEntryRepository timeEntryRepository;
  @InjectMocks private TaskService service;

  @Test
  void deleteTask_rejectsWhenTimeEntriesExist() {
    var taskId = UUID.randomUUID();
    var task = taskWithId(taskId, PROJECT_ID, "Task with time", MEMBER_ID);
    when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, "admin"))
        .thenReturn(new ProjectAccess(true, true, true, false, "admin"));
    when(timeEntryRepository.countByTaskId(taskId)).thenReturn(7L);

    assertThatThrownBy(() -> service.deleteTask(taskId, MEMBER_ID, "admin"))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("7 time entry/entries");
    verify(taskRepository, never()).delete(any());
  }

  @Test
  void deleteTask_deletesWhenNoTimeEntries() {
    var taskId = UUID.randomUUID();
    var task = taskWithId(taskId, PROJECT_ID, "Clean task", MEMBER_ID);
    when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, "admin"))
        .thenReturn(new ProjectAccess(true, true, true, false, "admin"));
    when(timeEntryRepository.countByTaskId(taskId)).thenReturn(0L);
    when(customerProjectRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());

    service.deleteTask(taskId, MEMBER_ID, "admin");

    verify(taskRepository).delete(task);
  }

  private static Task taskWithId(UUID id, UUID projectId, String title, UUID createdBy) {
    var task = new Task(projectId, title, null, "MEDIUM", null, null, createdBy);
    try {
      var idField = Task.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(task, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to set task ID", e);
    }
    return task;
  }
}
