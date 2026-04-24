package io.b2mash.b2b.b2bstrawman.customerbackend.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceRequestRepository;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
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
import io.b2mash.b2b.b2bstrawman.customerbackend.event.TaxContext;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.TimeEntryAggregatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDocumentView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.RequestItemSubmittedEvent;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestItem;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestItemRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestStatus;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link PortalEventHandler} verifying observable behavior (what data gets projected
 * into the portal read model) rather than internal method call sequences.
 *
 * <p>For full end-to-end integration tests of the event projection pipeline, see {@link
 * io.b2mash.b2b.b2bstrawman.customerbackend.EventProjectionIntegrationTest} and {@link
 * io.b2mash.b2b.b2bstrawman.customerbackend.InvoiceSyncIntegrationTest}.
 */
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
  @Mock private CommentRepository commentRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private ObjectMapper objectMapper;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private AcceptanceRequestRepository acceptanceRequestRepository;
  @Mock private InformationRequestRepository informationRequestRepository;
  @Mock private RequestItemRepository requestItemRepository;

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
            eq(projectId),
            eq(customerId),
            eq(ORG_ID),
            eq("Test Project"),
            eq("ACTIVE"),
            eq("A description"),
            any(Instant.class));
  }

  @Test
  void onCustomerProjectLinked_projectNotFound_doesNotProject() {
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

    verify(readModelRepo, times(2))
        .updatePortalProjectDetails(any(), any(), eq("Updated Name"), eq("ACTIVE"), eq("New desc"));
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
            eq(documentId),
            eq(ORG_ID),
            eq(linkedCustomer),
            eq(projectId),
            eq("file.pdf"),
            eq("application/pdf"),
            eq(1024L),
            eq("PROJECT"),
            eq("s3://bucket/key"),
            any(Instant.class));
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
            eq(documentId),
            eq(ORG_ID),
            eq(linkedCustomer),
            eq(projectId),
            eq("report.pdf"),
            eq("application/pdf"),
            eq(2048L),
            eq("PROJECT"),
            eq("s3://key"),
            any(Instant.class));
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
    verify(readModelRepo, times(3)).decrementDocumentCount(eq(projectId), any());
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

    verify(readModelRepo, times(2))
        .upsertPortalProjectSummary(
            eq(projectId),
            any(),
            eq(ORG_ID),
            eq(new BigDecimal("40.5")),
            eq(new BigDecimal("32.0")),
            eq(lastActivity));
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
  void onCustomerUpdated_notArchived_doesNotDeletePortalProjects() {
    var customerId = UUID.randomUUID();
    var event =
        new CustomerUpdatedEvent(customerId, "Acme", "acme@test.com", "ACTIVE", ORG_ID, TENANT_ID);

    handler.onCustomerUpdated(event);

    verify(readModelRepo, never()).deletePortalProject(any(), any());
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
            new TaxContext(List.of(), null, null, null, false, false));

    var line1 =
        TestIds.withId(
            new InvoiceLine(
                invoiceId,
                null,
                null,
                "Service A",
                new BigDecimal("10"),
                new BigDecimal("100.00"),
                0),
            lineId1);
    var line2 =
        TestIds.withId(
            new InvoiceLine(
                invoiceId,
                null,
                null,
                "Service B",
                new BigDecimal("5"),
                new BigDecimal("50.00"),
                1),
            lineId2);

    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId))
        .thenReturn(List.of(line1, line2));

    handler.onInvoiceSynced(event);

    // Verify invoice was upserted with correct key fields
    verify(readModelRepo)
        .upsertPortalInvoice(
            eq(invoiceId),
            eq(ORG_ID),
            eq(customerId),
            eq("INV-001"),
            eq("SENT"),
            eq(LocalDate.of(2026, 2, 1)),
            eq(LocalDate.of(2026, 3, 1)),
            eq(new BigDecimal("1000.00")),
            eq(new BigDecimal("150.00")),
            eq(new BigDecimal("1150.00")),
            eq("ZAR"),
            eq("Test notes"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(boolean.class),
            any(boolean.class));
    // Verify stale lines were cleared and new lines upserted
    verify(readModelRepo).deletePortalInvoiceLinesByInvoice(invoiceId);
    verify(readModelRepo, times(2))
        .upsertPortalInvoiceLine(
            any(),
            eq(invoiceId),
            any(),
            any(),
            any(),
            any(),
            any(int.class),
            any(),
            any(),
            any(),
            any(boolean.class));
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
            null);

    handler.onInvoiceSynced(event);

    verify(readModelRepo)
        .updatePortalInvoiceStatusAndPaidAt(
            eq(invoiceId), eq(ORG_ID), eq("PAID"), any(Instant.class));
    verify(readModelRepo, never()).deletePortalInvoiceLinesByInvoice(any());
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
            null);

    handler.onInvoiceSynced(event);

    verify(readModelRepo).deletePortalInvoice(invoiceId, ORG_ID);
    verify(readModelRepo, never()).deletePortalInvoiceLinesByInvoice(any());
  }

  // ── 15. RequestItemSubmitted → parent status mirrored (GAP-L-47) ─────

  @Test
  void onRequestItemSubmitted_mirrorsParentRequestStatusToReadModel() {
    var requestId = UUID.randomUUID();
    var itemId = UUID.randomUUID();
    var event =
        new RequestItemSubmittedEvent(
            "request_item.submitted",
            "request_item",
            itemId,
            null,
            null,
            "portal",
            TENANT_ID,
            ORG_ID,
            Instant.now(),
            java.util.Map.of(),
            requestId,
            itemId,
            UUID.randomUUID(),
            UUID.randomUUID());

    var item = org.mockito.Mockito.mock(RequestItem.class);
    when(item.getDocumentId()).thenReturn(null);
    when(item.getTextResponse()).thenReturn("answer");
    when(requestItemRepository.findById(itemId)).thenReturn(Optional.of(item));

    var parent = org.mockito.Mockito.mock(InformationRequest.class);
    when(parent.getStatus()).thenReturn(RequestStatus.IN_PROGRESS);
    when(parent.getCompletedAt()).thenReturn(null);
    when(informationRequestRepository.findById(requestId)).thenReturn(Optional.of(parent));

    handler.onRequestItemSubmitted(event);

    // Item-level projection + counts stay intact…
    verify(readModelRepo)
        .updatePortalRequestItemStatus(
            eq(itemId), eq("SUBMITTED"), eq(null), eq(null), eq("answer"));
    verify(readModelRepo).recalculatePortalRequestCounts(requestId);
    // …and the parent status is now mirrored into the read-model so the portal no longer lags.
    verify(readModelRepo).updatePortalRequestStatus(requestId, "IN_PROGRESS", null);
  }

  @Test
  void onRequestItemSubmitted_withMissingParent_doesNotBlowUp() {
    var requestId = UUID.randomUUID();
    var itemId = UUID.randomUUID();
    var event =
        new RequestItemSubmittedEvent(
            "request_item.submitted",
            "request_item",
            itemId,
            null,
            null,
            "portal",
            TENANT_ID,
            ORG_ID,
            Instant.now(),
            java.util.Map.of(),
            requestId,
            itemId,
            UUID.randomUUID(),
            UUID.randomUUID());

    var item = org.mockito.Mockito.mock(RequestItem.class);
    when(item.getDocumentId()).thenReturn(null);
    when(item.getTextResponse()).thenReturn(null);
    when(requestItemRepository.findById(itemId)).thenReturn(Optional.of(item));
    when(informationRequestRepository.findById(requestId)).thenReturn(Optional.empty());

    handler.onRequestItemSubmitted(event);

    verify(readModelRepo).recalculatePortalRequestCounts(requestId);
    // No parent → no parent-status mirror call (stays at whatever SENT was set on upsert).
    verify(readModelRepo, never()).updatePortalRequestStatus(any(), any(), any());
  }

  // ── Helper methods ─────────────────────────────────────────────────

  private Project createProject(UUID projectId, String name, String description) {
    var project = new Project(name, description, UUID.randomUUID());
    return TestIds.withId(project, projectId);
  }

  private Document createDocument(
      UUID documentId,
      UUID projectId,
      String fileName,
      String contentType,
      long size,
      String s3Key,
      String scope) {
    var doc =
        new Document(
            scope, projectId, null, fileName, contentType, size, UUID.randomUUID(), "SHARED");
    TestIds.withId(doc, documentId);
    TestIds.withField(doc, "s3Key", s3Key);
    TestIds.withField(doc, "uploadedAt", Instant.now());
    return doc;
  }
}
