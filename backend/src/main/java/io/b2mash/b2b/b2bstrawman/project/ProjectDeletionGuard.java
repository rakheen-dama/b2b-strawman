package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.DeleteGuard;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Encapsulates pre-delete linked resource checks for projects. Extracted from ProjectService to
 * reduce constructor bloat.
 */
@Service
class ProjectDeletionGuard {

  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final InvoiceRepository invoiceRepository;
  private final DocumentRepository documentRepository;

  ProjectDeletionGuard(
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      InvoiceRepository invoiceRepository,
      DocumentRepository documentRepository) {
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.invoiceRepository = invoiceRepository;
    this.documentRepository = documentRepository;
  }

  /** Checks that a project has no linked resources and can be safely deleted. */
  void checkAndExecute(UUID projectId) {
    DeleteGuard.forEntity("project", projectId)
        .checkCountZero(
            "task(s)",
            taskRepository.countByProjectId(projectId),
            "Delete or cancel all tasks before deleting the project.")
        .checkCountZero(
            "time entry/entries",
            timeEntryRepository.countByProjectId(projectId),
            "Remove all time entries before deleting the project.")
        .checkCountZero(
            "invoice(s)",
            invoiceRepository.countByProjectId(projectId),
            "Void or delete all invoices before deleting the project.")
        .checkCountZero(
            "document(s)",
            documentRepository.countByProjectId(projectId),
            "Delete all documents before deleting the project.")
        .execute();
  }
}
