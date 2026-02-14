package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoiceCrudIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_invoice_crud_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private InvoiceService invoiceService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerProjectService customerProjectService;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdAdmin;
  private UUID memberIdMember;
  private UUID projectId1;
  private UUID projectId2;
  private UUID customerId;
  private UUID archivedCustomerId;
  private UUID taskId1;
  private UUID taskId2;
  // Set A: for happy path (test 1) — these get billed and stay billed
  private UUID teIdA1;
  private UUID teIdA2;
  // Set B: for delete test (test 10)
  private UUID teIdB1;
  private UUID teIdB2;
  // Set C: for getInvoice and addLine tests (test 11, 14)
  private UUID teIdC1;
  private UUID teIdC2;
  // Non-billable entry
  private UUID teIdNonBillable;
  // Entry on project2 (not linked to customer)
  private UUID teIdOtherProject;

  private UUID createdInvoiceId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice CRUD Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdAdmin =
        UUID.fromString(
            syncMember(ORG_ID, "user_inv_admin", "inv_admin@test.com", "Admin User", "admin"));
    memberIdMember =
        UUID.fromString(
            syncMember(ORG_ID, "user_inv_member", "inv_member@test.com", "Member User", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberIdAdmin)
        .where(RequestScopes.ORG_ROLE, "admin")
        .run(
            () -> {
              // Projects
              projectId1 =
                  projectService
                      .createProject("Invoice Test Project 1", "P1", memberIdAdmin)
                      .getId();
              projectId2 =
                  projectService
                      .createProject("Invoice Test Project 2", "P2", memberIdAdmin)
                      .getId();

              // Active customer linked to project1
              customerId =
                  customerService
                      .createCustomer(
                          "Test Invoice Customer",
                          "inv-customer@test.com",
                          null,
                          null,
                          null,
                          "123 Test St",
                          memberIdAdmin)
                      .getId();

              // Archived customer for test 2
              archivedCustomerId =
                  customerService
                      .createCustomer(
                          "Archived Customer",
                          "archived-inv-cust@test.com",
                          null,
                          null,
                          null,
                          null,
                          memberIdAdmin)
                      .getId();
              customerService.archiveCustomer(archivedCustomerId);

              customerProjectService.linkCustomerToProject(
                  customerId, projectId1, memberIdAdmin, memberIdAdmin, "admin");

              // Tasks
              Task task1 =
                  taskService.createTask(
                      projectId1,
                      "Website Redesign",
                      "desc",
                      "HIGH",
                      null,
                      null,
                      memberIdAdmin,
                      "admin");
              taskId1 = task1.getId();

              Task task2 =
                  taskService.createTask(
                      projectId2,
                      "API Development",
                      "desc",
                      "MEDIUM",
                      null,
                      null,
                      memberIdAdmin,
                      "admin");
              taskId2 = task2.getId();

              // --- Set A: for happy path test 1 ---
              teIdA1 =
                  createBillableEntry(taskId1, LocalDate.of(2026, 2, 10), 120, "150.00", "USD");
              teIdA2 = createBillableEntry(taskId1, LocalDate.of(2026, 2, 11), 90, "150.00", "USD");

              // --- Set B: for delete test 10 ---
              teIdB1 = createBillableEntry(taskId1, LocalDate.of(2026, 2, 12), 60, "150.00", "USD");
              teIdB2 = createBillableEntry(taskId1, LocalDate.of(2026, 2, 13), 45, "150.00", "USD");

              // --- Set C: for getInvoice test 11, addLine test 14 ---
              teIdC1 =
                  createBillableEntry(taskId1, LocalDate.of(2026, 2, 14), 120, "150.00", "USD");
              teIdC2 = createBillableEntry(taskId1, LocalDate.of(2026, 2, 15), 90, "150.00", "USD");

              // Non-billable entry
              TimeEntry teNB =
                  timeEntryService.createTimeEntry(
                      taskId1,
                      LocalDate.of(2026, 2, 16),
                      60,
                      false,
                      null,
                      "Internal meeting",
                      memberIdAdmin,
                      "admin");
              teIdNonBillable = teNB.getId();

              // Entry on project2 (not linked to customer)
              teIdOtherProject =
                  createBillableEntry(taskId2, LocalDate.of(2026, 2, 10), 60, "200.00", "EUR");
            });
  }

  private UUID createBillableEntry(
      UUID taskId, LocalDate date, int minutes, String rate, String currency) {
    TimeEntry te =
        timeEntryService.createTimeEntry(
            taskId, date, minutes, true, null, "Work on " + date, memberIdAdmin, "admin");
    te.snapshotBillingRate(new BigDecimal(rate), currency);
    timeEntryRepository.save(te);
    return te.getId();
  }

  @Test
  @Order(1)
  void createDraft_happyPath() {
    Invoice invoice =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                invoiceService.createDraft(
                    customerId,
                    "USD",
                    List.of(teIdA1, teIdA2),
                    LocalDate.of(2026, 3, 15),
                    "January consulting work",
                    "Net 30",
                    memberIdAdmin));

    assertThat(invoice).isNotNull();
    assertThat(invoice.getId()).isNotNull();
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
    assertThat(invoice.getCurrency()).isEqualTo("USD");
    assertThat(invoice.getCustomerId()).isEqualTo(customerId);
    assertThat(invoice.getCustomerName()).isEqualTo("Test Invoice Customer");
    assertThat(invoice.getCustomerEmail()).isEqualTo("inv-customer@test.com");
    assertThat(invoice.getCustomerAddress()).isEqualTo("123 Test St");
    assertThat(invoice.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    assertThat(invoice.getNotes()).isEqualTo("January consulting work");
    assertThat(invoice.getPaymentTerms()).isEqualTo("Net 30");
    assertThat(invoice.getCreatedBy()).isEqualTo(memberIdAdmin);
    assertThat(invoice.getInvoiceNumber()).isNull();

    // 2h * $150 + 1.5h * $150 = $300 + $225 = $525
    assertThat(invoice.getSubtotal()).isEqualByComparingTo("525.00");
    assertThat(invoice.getTaxAmount()).isEqualByComparingTo("0.00");
    assertThat(invoice.getTotal()).isEqualByComparingTo("525.00");

    List<InvoiceLine> lines =
        runInTenantAs(
            memberIdAdmin, "admin", () -> invoiceService.getInvoiceLines(invoice.getId()));

    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).getTimeEntryId()).isEqualTo(teIdA1);
    assertThat(lines.get(0).getDescription()).contains("Website Redesign");
    assertThat(lines.get(0).getDescription()).contains("Admin User");
    assertThat(lines.get(0).getQuantity()).isEqualByComparingTo("2.0000");
    assertThat(lines.get(0).getUnitPrice()).isEqualByComparingTo("150.00");
    assertThat(lines.get(0).getAmount()).isEqualByComparingTo("300.00");

    assertThat(lines.get(1).getTimeEntryId()).isEqualTo(teIdA2);
    assertThat(lines.get(1).getQuantity()).isEqualByComparingTo("1.5000");
    assertThat(lines.get(1).getAmount()).isEqualByComparingTo("225.00");

    createdInvoiceId = invoice.getId();
  }

  @Test
  @Order(2)
  void createDraft_rejectsArchivedCustomer() {
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () ->
                        invoiceService.createDraft(
                            archivedCustomerId, "USD", List.of(), null, null, null, memberIdAdmin)))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("not active");
  }

  @Test
  @Order(3)
  void createDraft_rejectsAlreadyBilledTimeEntries() {
    // teIdA1 was billed in test 1
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () ->
                        invoiceService.createDraft(
                            customerId, "USD", List.of(teIdA1), null, null, null, memberIdAdmin)))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("already billed");
  }

  @Test
  @Order(4)
  void createDraft_rejectsNonBillableEntries() {
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () ->
                        invoiceService.createDraft(
                            customerId,
                            "USD",
                            List.of(teIdNonBillable),
                            null,
                            null,
                            null,
                            memberIdAdmin)))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("not billable");
  }

  @Test
  @Order(5)
  void createDraft_rejectsEntriesNotBelongingToCustomerProjects() {
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () ->
                        invoiceService.createDraft(
                            customerId,
                            "USD",
                            List.of(teIdOtherProject),
                            null,
                            null,
                            null,
                            memberIdAdmin)))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("not linked to customer");
  }

  @Test
  @Order(6)
  void createDraft_rejectsMismatchedCurrency() {
    // Link project2 to customer so project check passes but currency fails
    runInTenantAs(
        memberIdAdmin,
        "admin",
        () -> {
          customerProjectService.linkCustomerToProject(
              customerId, projectId2, memberIdAdmin, memberIdAdmin, "admin");
          return null;
        });

    // teIdOtherProject has EUR, request USD
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () ->
                        invoiceService.createDraft(
                            customerId,
                            "USD",
                            List.of(teIdOtherProject),
                            null,
                            null,
                            null,
                            memberIdAdmin)))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Currency mismatch");

    // Restore state
    runInTenantAs(
        memberIdAdmin,
        "admin",
        () -> {
          customerProjectService.unlinkCustomerFromProject(
              customerId, projectId2, memberIdAdmin, "admin");
          return null;
        });
  }

  @Test
  @Order(7)
  void updateDraft_success() {
    Invoice updated =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                invoiceService.updateDraft(
                    createdInvoiceId,
                    LocalDate.of(2026, 4, 1),
                    "Updated notes",
                    "Net 60",
                    new BigDecimal("52.50")));

    assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(updated.getNotes()).isEqualTo("Updated notes");
    assertThat(updated.getPaymentTerms()).isEqualTo("Net 60");
    assertThat(updated.getTaxAmount()).isEqualByComparingTo("52.50");
    assertThat(updated.getTotal()).isEqualByComparingTo("577.50");
  }

  @Test
  @Order(8)
  void updateNonDraft_rejectsModification() {
    // Approve the invoice to make it non-DRAFT
    runInTenantAs(
        memberIdAdmin,
        "admin",
        () -> {
          Invoice inv = invoiceRepository.findOneById(createdInvoiceId).orElseThrow();
          inv.approve(memberIdAdmin);
          inv.setInvoiceNumber("INV-0001");
          return invoiceRepository.save(inv);
        });

    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () ->
                        invoiceService.updateDraft(
                            createdInvoiceId, null, "Cannot update", null, null)))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("DRAFT");
  }

  @Test
  @Order(9)
  void deleteNonDraft_rejectsDeletion() {
    // createdInvoiceId is APPROVED from test 8
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () -> {
                      invoiceService.deleteDraft(createdInvoiceId);
                      return null;
                    }))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("DRAFT");
  }

  @Test
  @Order(10)
  void deleteDraft_success() {
    // Create a draft with set B entries (fresh, never billed)
    Invoice draft =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                invoiceService.createDraft(
                    customerId, "USD", List.of(teIdB1, teIdB2), null, null, null, memberIdAdmin));

    UUID draftId = draft.getId();

    // Delete it
    runInTenantAs(
        memberIdAdmin,
        "admin",
        () -> {
          invoiceService.deleteDraft(draftId);
          return null;
        });

    // Verify it's gone
    assertThatThrownBy(
            () -> runInTenantAs(memberIdAdmin, "admin", () -> invoiceService.getInvoice(draftId)))
        .isInstanceOf(ResourceNotFoundException.class);

    // Verify time entries are unbilled again
    TimeEntry te =
        runInTenantAs(
            memberIdAdmin, "admin", () -> timeEntryRepository.findOneById(teIdB1).orElseThrow());
    assertThat(te.isBilled()).isFalse();
  }

  @Test
  @Order(11)
  void getInvoice_withLines() {
    // Create a draft with set C entries
    Invoice draft =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                invoiceService.createDraft(
                    customerId,
                    "USD",
                    List.of(teIdC1, teIdC2),
                    LocalDate.of(2026, 3, 15),
                    "Get test notes",
                    null,
                    memberIdAdmin));

    Invoice retrieved =
        runInTenantAs(memberIdAdmin, "admin", () -> invoiceService.getInvoice(draft.getId()));

    assertThat(retrieved).isNotNull();
    assertThat(retrieved.getId()).isEqualTo(draft.getId());
    assertThat(retrieved.getCustomerId()).isEqualTo(customerId);
    assertThat(retrieved.getStatus()).isEqualTo(InvoiceStatus.DRAFT);

    List<InvoiceLine> lines =
        runInTenantAs(memberIdAdmin, "admin", () -> invoiceService.getInvoiceLines(draft.getId()));

    assertThat(lines).hasSize(2);

    // Store for later tests
    createdInvoiceId = draft.getId();
  }

  @Test
  @Order(12)
  void listInvoices_withPagination() {
    var page =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () -> invoiceService.listInvoices(null, null, null, null, PageRequest.of(0, 10)));

    // We have: invoice from test 1 (APPROVED), draft from test 11 (DRAFT)
    assertThat(page).isNotNull();
    assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @Order(13)
  void listInvoices_filteredByStatusAndCustomer() {
    // Filter by DRAFT status
    var draftsPage =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                invoiceService.listInvoices(
                    null, InvoiceStatus.DRAFT, null, null, PageRequest.of(0, 10)));

    assertThat(draftsPage.getContent()).isNotEmpty();
    assertThat(draftsPage.getContent()).allMatch(inv -> inv.getStatus() == InvoiceStatus.DRAFT);

    // Filter by customer
    var customerPage =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () -> invoiceService.listInvoices(customerId, null, null, null, PageRequest.of(0, 10)));

    assertThat(customerPage.getContent()).isNotEmpty();
    assertThat(customerPage.getContent()).allMatch(inv -> inv.getCustomerId().equals(customerId));
  }

  @Test
  @Order(14)
  void addManualLine_recomputesTotals() {
    // createdInvoiceId is the DRAFT from test 11
    Invoice before =
        runInTenantAs(memberIdAdmin, "admin", () -> invoiceService.getInvoice(createdInvoiceId));

    BigDecimal subtotalBefore = before.getSubtotal();

    // Add a manual line: 1 x $100 = $100
    Invoice afterAdd =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                invoiceService.addLine(
                    createdInvoiceId,
                    null,
                    "Consulting fee",
                    new BigDecimal("1.0000"),
                    new BigDecimal("100.00"),
                    99));

    BigDecimal expectedSubtotal =
        subtotalBefore.add(new BigDecimal("100.00")).setScale(2, RoundingMode.HALF_UP);
    assertThat(afterAdd.getSubtotal()).isEqualByComparingTo(expectedSubtotal);

    // Verify the line was added
    List<InvoiceLine> lines =
        runInTenantAs(
            memberIdAdmin, "admin", () -> invoiceService.getInvoiceLines(createdInvoiceId));

    assertThat(lines).hasSize(3); // 2 time-based + 1 manual
    InvoiceLine manualLine =
        lines.stream().filter(l -> l.getTimeEntryId() == null).findFirst().orElseThrow();
    assertThat(manualLine.getDescription()).isEqualTo("Consulting fee");
    assertThat(manualLine.getAmount()).isEqualByComparingTo("100.00");
  }

  // --- Helpers ---

  private <T> T runInTenantAs(UUID actorId, String role, Callable<T> callable) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.MEMBER_ID, actorId)
          .where(RequestScopes.ORG_ROLE, role)
          .call(() -> callable.call());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
