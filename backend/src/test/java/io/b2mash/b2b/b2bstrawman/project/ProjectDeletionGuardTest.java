package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectDeletionGuardTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();

  @Mock private TaskRepository taskRepository;
  @Mock private TimeEntryRepository timeEntryRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private DocumentRepository documentRepository;
  @InjectMocks private ProjectDeletionGuard guard;

  @Test
  void passesWhenNoLinkedResources() {
    when(taskRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(timeEntryRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(invoiceRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(documentRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);

    assertThatCode(() -> guard.checkAndExecute(PROJECT_ID)).doesNotThrowAnyException();
  }

  @Test
  void rejectsWhenTasksExist() {
    when(taskRepository.countByProjectId(PROJECT_ID)).thenReturn(3L);
    when(timeEntryRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(invoiceRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(documentRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);

    assertThatThrownBy(() -> guard.checkAndExecute(PROJECT_ID))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("task");
  }

  @Test
  void rejectsWhenTimeEntriesExist() {
    when(taskRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(timeEntryRepository.countByProjectId(PROJECT_ID)).thenReturn(5L);
    when(invoiceRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(documentRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);

    assertThatThrownBy(() -> guard.checkAndExecute(PROJECT_ID))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("time entry");
  }

  @Test
  void rejectsWhenInvoicesExist() {
    when(taskRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(timeEntryRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(invoiceRepository.countByProjectId(PROJECT_ID)).thenReturn(2L);
    when(documentRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);

    assertThatThrownBy(() -> guard.checkAndExecute(PROJECT_ID))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("invoice");
  }

  @Test
  void rejectsWhenDocumentsExist() {
    when(taskRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(timeEntryRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(invoiceRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
    when(documentRepository.countByProjectId(PROJECT_ID)).thenReturn(1L);

    assertThatThrownBy(() -> guard.checkAndExecute(PROJECT_ID))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("document");
  }
}
