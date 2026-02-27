package io.b2mash.b2b.b2bstrawman.customerbackend.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerProjectLinkedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerProjectUnlinkedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.InvoiceSyncEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.TimeEntryAggregatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDocumentView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortalEventHandlerTest {

  private static final String ORG_ID = "org_test";
  private static final String TENANT_ID = "tenant_test";

  @Mock private PortalReadModelRepository readModelRepo;
  @Mock private ProjectRepository projectRepository;
  @Mock private DocumentRepository documentRepository;
  @Mock private CustomerProjectRepository customerProjectRepository;
  @Mock private io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository invoiceRepository;
  @Mock private io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository invoiceLineRepository;

  @InjectMocks private PortalEventHandler handler;

  // ── 1. CustomerProjectLinked -> portal_project created ─────────────

  @Test
  void onCustomerProjectLinked_createsPortalProject() {
    var customerId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var event = new CustomerProjectLinkedEvent(customerId, projectId, ORG_ID, TENANT_ID);

    var project = createProject(projectId, "Test Project", "A description");
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

    handler.onCustomerProjectLinked(event);

    verify(readModelRepo)
        .upsertPortalProject(
            projectId,
            customerId,
            ORG_ID,
            "Test Project",
            "ACTIVE",
            "A description",
            project.getCreatedAt());
  }

  @Test
  void onCustomerProjectLinked_projectNotFound_logsWarning() {
    var customerId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var event = new CustomerProjectLinkedEvent(customerId, projectId, ORG_ID, TENANT_ID);

    when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

    handler.onCustomerProjectLinked(event);

    verify(readModelRepo, never())
        .upsertPortalProject(any(), any(), any(), any(), any(), any(), any());
  }

  // ── 2. CustomerProjectUnlinked -> portal_project deleted ───────────

  @Test
  void onCustomerProjectUnlinked_deletesPortalProject() {
    var customerId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var event = new CustomerProjectUnlinkedEvent(customerId, projectId, ORG_ID, TENANT_ID);

    handler.onCustomerProjectUnlinked(event);

    verify(readModelRepo).deletePortalProject(projectId, customerId);
  }

  // ── 3. ProjectUpdated -> all linked customer projections updated ───

  @Test
  void onProjectUpdated_updatesAllLinkedCustomerProjections() {
    var projectId = UUID.randomUUID();
    var customer1 = UUID.randomUUID();
    var customer2 = UUID.randomUUID();
    var event =
        new ProjectUpdatedEvent(projectId, "Updated Name", "New desc", "ACTIVE", ORG_ID, TENANT_ID);

    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(customer1, customer2));

    handler.onProjectUpdated(event);

    verify(readModelRepo)
        .updatePortalProjectDetails(projectId, customer1, "Updated Name", "ACTIVE", "New desc");
    verify(readModelRepo)
        .updatePortalProjectDetails(projectId, customer2, "Updated Name", "ACTIVE", "New desc");
  }

  // ── 4. DocumentCreated (SHARED) -> portal_document + count ─────────

  @Test
  void onDocumentCreated_shared_createsPortalDocumentAndIncrementsCount() {
    var documentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var customerId = UUID.randomUUID();
    var linkedCustomer = UUID.randomUUID();
    var event =
        new DocumentCreatedEvent(
            documentId,
            projectId,
            customerId,
            "file.pdf",
            "PROJECT",
            "SHARED",
            "s3://bucket/key",
            1024L,
            "application/pdf",
            ORG_ID,
            TENANT_ID);

    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(linkedCustomer));

    handler.onDocumentCreated(event);

    verify(readModelRepo)
        .upsertPortalDocument(
            documentId,
            ORG_ID,
            linkedCustomer,
            projectId,
            "file.pdf",
            "application/pdf",
            1024L,
            "PROJECT",
            "s3://bucket/key",
            event.getOccurredAt());
    verify(readModelRepo).incrementDocumentCount(projectId, linkedCustomer);
  }

  // ── 5. DocumentCreated (INTERNAL) -> no projection ─────────────────

  @Test
  void onDocumentCreated_internal_skipsProjection() {
    var event =
        new DocumentCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "file.pdf",
            "PROJECT",
            "INTERNAL",
            "s3://bucket/key",
            1024L,
            "application/pdf",
            ORG_ID,
            TENANT_ID);

    handler.onDocumentCreated(event);

    verify(readModelRepo, never())
        .upsertPortalDocument(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(readModelRepo, never()).incrementDocumentCount(any(), any());
  }

  // ── 6. DocumentVisibilityChanged INTERNAL->SHARED -> projected ─────

  @Test
  void onDocumentVisibilityChanged_toShared_projectsDocument() {
    var documentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var linkedCustomer = UUID.randomUUID();
    var event =
        new DocumentVisibilityChangedEvent(documentId, "SHARED", "INTERNAL", ORG_ID, TENANT_ID);

    var doc =
        createDocument(
            documentId, projectId, "report.pdf", "application/pdf", 2048L, "s3://key", "PROJECT");
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(linkedCustomer));

    handler.onDocumentVisibilityChanged(event);

    verify(readModelRepo)
        .upsertPortalDocument(
            documentId,
            ORG_ID,
            linkedCustomer,
            projectId,
            "report.pdf",
            "application/pdf",
            2048L,
            "PROJECT",
            "s3://key",
            doc.getUploadedAt());
    verify(readModelRepo).incrementDocumentCount(projectId, linkedCustomer);
  }

  // ── 7. DocumentVisibilityChanged SHARED->INTERNAL -> removed ───────

  @Test
  void onDocumentVisibilityChanged_fromShared_removesDocumentAndDecrementsCount() {
    var documentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var linkedCustomer = UUID.randomUUID();
    var event =
        new DocumentVisibilityChangedEvent(documentId, "INTERNAL", "SHARED", ORG_ID, TENANT_ID);

    var portalDoc =
        new PortalDocumentView(
            documentId,
            ORG_ID,
            linkedCustomer,
            projectId,
            "report.pdf",
            "application/pdf",
            2048L,
            "PROJECT",
            "s3://key",
            Instant.now(),
            Instant.now());
    when(readModelRepo.findPortalDocumentById(documentId, ORG_ID))
        .thenReturn(Optional.of(portalDoc));
    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(linkedCustomer));

    handler.onDocumentVisibilityChanged(event);

    verify(readModelRepo).deletePortalDocument(documentId, ORG_ID);
    verify(readModelRepo).decrementDocumentCount(projectId, linkedCustomer);
  }

  // ── 8. DocumentDeleted -> portal_document removed ──────────────────

  @Test
  void onDocumentDeleted_removesPortalDocumentAndDecrementsCountForAllLinkedCustomers() {
    var documentId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var customer1 = UUID.randomUUID();
    var customer2 = UUID.randomUUID();
    var customer3 = UUID.randomUUID();
    var event = new DocumentDeletedEvent(documentId, ORG_ID, TENANT_ID);

    var portalDoc =
        new PortalDocumentView(
            documentId,
            ORG_ID,
            customer1,
            projectId,
            "file.pdf",
            "application/pdf",
            1024L,
            "PROJECT",
            "s3://key",
            Instant.now(),
            Instant.now());
    when(readModelRepo.findPortalDocumentById(documentId, ORG_ID))
        .thenReturn(Optional.of(portalDoc));
    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(customer1, customer2, customer3));

    handler.onDocumentDeleted(event);

    verify(readModelRepo).deletePortalDocument(documentId, ORG_ID);
    verify(readModelRepo).decrementDocumentCount(projectId, customer1);
    verify(readModelRepo).decrementDocumentCount(projectId, customer2);
    verify(readModelRepo).decrementDocumentCount(projectId, customer3);
  }

  @Test
  void onDocumentDeleted_notInPortal_deletesWithoutDecrement() {
    var documentId = UUID.randomUUID();
    var event = new DocumentDeletedEvent(documentId, ORG_ID, TENANT_ID);

    when(readModelRepo.findPortalDocumentById(documentId, ORG_ID)).thenReturn(Optional.empty());

    handler.onDocumentDeleted(event);

    verify(readModelRepo).deletePortalDocument(documentId, ORG_ID);
    verify(readModelRepo, never()).decrementDocumentCount(any(), any());
  }

  // ── 9. Handler exception does not propagate ────────────────────────

  @Test
  void handlerException_doesNotPropagate() {
    var customerId = UUID.randomUUID();
    var projectId = UUID.randomUUID();
    var event = new CustomerProjectLinkedEvent(customerId, projectId, ORG_ID, TENANT_ID);

    when(projectRepository.findById(projectId))
        .thenThrow(new RuntimeException("Simulated DB failure"));

    // Should not throw -- exception is caught and logged
    handler.onCustomerProjectLinked(event);

    verify(readModelRepo, never())
        .upsertPortalProject(any(), any(), any(), any(), any(), any(), any());
  }

  // ── 10. TimeEntryAggregated -> summary upserted ────────────────────

  @Test
  void onTimeEntryAggregated_upsertsSummaryForAllLinkedCustomers() {
    var projectId = UUID.randomUUID();
    var customer1 = UUID.randomUUID();
    var customer2 = UUID.randomUUID();
    var lastActivity = Instant.now();
    var event =
        new TimeEntryAggregatedEvent(
            projectId,
            new BigDecimal("40.5"),
            new BigDecimal("32.0"),
            lastActivity,
            ORG_ID,
            TENANT_ID);

    when(readModelRepo.findCustomerIdsByProjectId(projectId, ORG_ID))
        .thenReturn(List.of(customer1, customer2));

    handler.onTimeEntryAggregated(event);

    verify(readModelRepo)
        .upsertPortalProjectSummary(
            projectId,
            customer1,
            ORG_ID,
            new BigDecimal("40.5"),
            new BigDecimal("32.0"),
            lastActivity);
    verify(readModelRepo)
        .upsertPortalProjectSummary(
            projectId,
            customer2,
            ORG_ID,
            new BigDecimal("40.5"),
            new BigDecimal("32.0"),
            lastActivity);
  }

  // ── 11. CustomerUpdated ARCHIVED -> cleans up projections ──────────

  @Test
  void onCustomerUpdated_archived_deletesAllLinkedProjections() {
    var customerId = UUID.randomUUID();
    var projectId1 = UUID.randomUUID();
    var projectId2 = UUID.randomUUID();
    var event =
        new CustomerUpdatedEvent(
            customerId, "Acme", "acme@test.com", "ARCHIVED", ORG_ID, TENANT_ID);

    var link1 = new CustomerProject(customerId, projectId1, UUID.randomUUID());
    var link2 = new CustomerProject(customerId, projectId2, UUID.randomUUID());
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of(link1, link2));

    handler.onCustomerUpdated(event);

    verify(readModelRepo).deletePortalProject(projectId1, customerId);
    verify(readModelRepo).deletePortalProject(projectId2, customerId);
  }

  @Test
  void onCustomerUpdated_notArchived_doesNothing() {
    var customerId = UUID.randomUUID();
    var event =
        new CustomerUpdatedEvent(customerId, "Acme", "acme@test.com", "ACTIVE", ORG_ID, TENANT_ID);

    handler.onCustomerUpdated(event);

    verifyNoInteractions(readModelRepo);
  }

  // ── 12. InvoiceSynced SENT -> upserts invoice + line items ──────────

  @Test
  void onInvoiceSynced_sent_upsertsInvoiceAndLineItems() {
    var invoiceId = UUID.randomUUID();
    var customerId = UUID.randomUUID();
    var lineId1 = UUID.randomUUID();
    var lineId2 = UUID.randomUUID();
    var event =
        new InvoiceSyncEvent(
            invoiceId,
            customerId,
            "INV-001",
            "SENT",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 3, 1),
            new BigDecimal("1000.00"),
            new BigDecimal("150.00"),
            new BigDecimal("1150.00"),
            "ZAR",
            "Test notes",
            null,
            null,
            ORG_ID,
            TENANT_ID,
            List.of(),
            null,
            null,
            null,
            false,
            false);

    var line1 =
        new InvoiceLine(
            invoiceId, null, null, "Service A", new BigDecimal("10"), new BigDecimal("100.00"), 0);
    setFieldSilent(line1, "id", lineId1);
    var line2 =
        new InvoiceLine(
            invoiceId, null, null, "Service B", new BigDecimal("5"), new BigDecimal("50.00"), 1);
    setFieldSilent(line2, "id", lineId2);

    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId))
        .thenReturn(List.of(line1, line2));

    handler.onInvoiceSynced(event);

    verify(readModelRepo)
        .upsertPortalInvoice(
            invoiceId,
            ORG_ID,
            customerId,
            "INV-001",
            "SENT",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 3, 1),
            new BigDecimal("1000.00"),
            new BigDecimal("150.00"),
            new BigDecimal("1150.00"),
            "ZAR",
            "Test notes",
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            false);
    verify(readModelRepo).deletePortalInvoiceLinesByInvoice(invoiceId);
    verify(readModelRepo)
        .upsertPortalInvoiceLine(
            lineId1,
            invoiceId,
            "Service A",
            new BigDecimal("10"),
            new BigDecimal("100.00"),
            line1.getAmount(),
            0,
            null,
            null,
            null,
            false);
    verify(readModelRepo)
        .upsertPortalInvoiceLine(
            lineId2,
            invoiceId,
            "Service B",
            new BigDecimal("5"),
            new BigDecimal("50.00"),
            line2.getAmount(),
            1,
            null,
            null,
            null,
            false);
  }

  // ── 13. InvoiceSynced PAID -> updates status only ──────────────────

  @Test
  void onInvoiceSynced_paid_updatesStatusOnly() {
    var invoiceId = UUID.randomUUID();
    var customerId = UUID.randomUUID();
    var event =
        new InvoiceSyncEvent(
            invoiceId,
            customerId,
            "INV-002",
            "PAID",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 3, 1),
            new BigDecimal("500.00"),
            BigDecimal.ZERO,
            new BigDecimal("500.00"),
            "ZAR",
            null,
            null,
            null,
            ORG_ID,
            TENANT_ID,
            List.of(),
            null,
            null,
            null,
            false,
            false);

    handler.onInvoiceSynced(event);

    verify(readModelRepo)
        .updatePortalInvoiceStatusAndPaidAt(eq(invoiceId), eq(ORG_ID), eq("PAID"), any());
    verify(readModelRepo, never())
        .upsertPortalInvoice(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyBoolean());
  }

  // ── 14. InvoiceSynced VOID -> deletes invoice ──────────────────────

  @Test
  void onInvoiceSynced_void_deletesInvoice() {
    var invoiceId = UUID.randomUUID();
    var customerId = UUID.randomUUID();
    var event =
        new InvoiceSyncEvent(
            invoiceId,
            customerId,
            "INV-003",
            "VOID",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 3, 1),
            new BigDecimal("200.00"),
            BigDecimal.ZERO,
            new BigDecimal("200.00"),
            "ZAR",
            null,
            null,
            null,
            ORG_ID,
            TENANT_ID,
            List.of(),
            null,
            null,
            null,
            false,
            false);

    handler.onInvoiceSynced(event);

    verify(readModelRepo).deletePortalInvoice(invoiceId, ORG_ID);
    verify(readModelRepo, never())
        .upsertPortalInvoice(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyBoolean());
  }

  // ── Helper methods ─────────────────────────────────────────────────

  private void setFieldSilent(Object target, String fieldName, Object value) {
    try {
      setField(target, fieldName, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field " + fieldName, e);
    }
  }

  private Project createProject(UUID projectId, String name, String description) {
    try {
      var project = new Project(name, description, UUID.randomUUID());
      setField(project, "id", projectId);
      return project;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create test Project", e);
    }
  }

  private Document createDocument(
      UUID documentId,
      UUID projectId,
      String fileName,
      String contentType,
      long size,
      String s3Key,
      String scope) {
    try {
      var ctor = Document.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      var doc = ctor.newInstance();
      setField(doc, "id", documentId);
      setField(doc, "projectId", projectId);
      setField(doc, "fileName", fileName);
      setField(doc, "contentType", contentType);
      setField(doc, "size", size);
      setField(doc, "s3Key", s3Key);
      setField(doc, "scope", scope);
      setField(doc, "uploadedAt", Instant.now());
      return doc;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create test Document", e);
    }
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
