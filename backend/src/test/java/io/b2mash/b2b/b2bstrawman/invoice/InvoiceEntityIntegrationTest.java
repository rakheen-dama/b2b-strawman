package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoiceEntityIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_invoice_entity_crud_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectService projectService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskService taskService;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private InvoiceNumberService invoiceNumberService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;
  private UUID timeEntryId1;
  private UUID timeEntryId2;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Invoice Entity CRUD Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_invoice_crud_owner",
                "invoice_crud_owner@test.com",
                "Owner",
                "owner"));

    // Create customer, project, task, and time entries within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Invoice Entity Test Customer",
                      "invoice-entity-customer@test.com",
                      "+1234567890",
                      "ID123",
                      "Test notes",
                      "123 Test Street\nTest City, TS 12345",
                      memberIdOwner);
              customerId = customer.getId();

              var project =
                  projectService.createProject("Invoice CRUD Test Project", "Test", memberIdOwner);
              projectId = project.getId();

              var task =
                  taskService.createTask(
                      projectId,
                      "Test Task",
                      "Description",
                      null,
                      null,
                      null,
                      memberIdOwner,
                      "owner");
              taskId = task.getId();

              var timeEntry1 =
                  timeEntryService.createTimeEntry(
                      taskId,
                      LocalDate.now().minusDays(2),
                      240,
                      true,
                      null,
                      "Development work",
                      memberIdOwner,
                      "owner");
              timeEntryId1 = timeEntry1.getId();

              var timeEntry2 =
                  timeEntryService.createTimeEntry(
                      taskId,
                      LocalDate.now().minusDays(1),
                      120,
                      true,
                      null,
                      "Code review",
                      memberIdOwner,
                      "owner");
              timeEntryId2 = timeEntry2.getId();
            });
  }

  private String syncMember(String orgId, String userId, String email, String name, String role)
      throws Exception {
    String payload =
        """
        {
          "clerkOrgId": "%s",
          "clerkUserId": "%s",
          "email": "%s",
          "name": "%s",
          "orgRole": "%s"
        }
        """
            .formatted(orgId, userId, email, name, role);

    var response =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    return JsonPath.read(response.getResponse().getContentAsString(), "$.memberId");
  }

  private <T> T runInTenant(ScopedValue.CallableOp<T, Exception> op) throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(op);
  }

  private void runInTenantVoid(Runnable op) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(op);
  }

  @Test
  @Order(1)
  void testInvoiceEntityPersistAndRetrieve() throws Exception {
    var invoiceId =
        runInTenant(
            () -> {
              // Fetch customer to get address
              Customer customer = customerRepository.findOneById(customerId).orElseThrow();

              // Create invoice
              var invoice =
                  new Invoice(
                      customerId,
                      "USD",
                      customer.getName(),
                      customer.getEmail(),
                      customer.getAddress(),
                      "Test Org",
                      memberIdOwner);

              invoice.setDueDate(LocalDate.now().plusDays(30));
              invoice.setNotes("Test invoice notes");
              invoice.setPaymentTerms("Net 30");

              invoice = invoiceRepository.save(invoice);
              assertThat(invoice.getId()).isNotNull();
              assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
              assertThat(invoice.getInvoiceNumber()).isNull(); // No number until approved
              assertThat(invoice.getCustomerName()).isEqualTo("Invoice Entity Test Customer");
              assertThat(invoice.getCustomerAddress())
                  .isEqualTo("123 Test Street\nTest City, TS 12345");

              return invoice.getId();
            });

    // Verify retrieval
    runInTenantVoid(
        () -> {
          var invoice = invoiceRepository.findOneById(invoiceId).orElseThrow();
          assertThat(invoice.getCustomerId()).isEqualTo(customerId);
          assertThat(invoice.getCurrency()).isEqualTo("USD");
          assertThat(invoice.getNotes()).isEqualTo("Test invoice notes");
          assertThat(invoice.getPaymentTerms()).isEqualTo("Net 30");
        });
  }

  @Test
  @Order(2)
  void testInvoiceLineEntityPersistAndRetrieve() throws Exception {
    var savedIds =
        runInTenant(
            () -> {
              // Create draft invoice
              Customer customer = customerRepository.findOneById(customerId).orElseThrow();
              var invoice =
                  new Invoice(
                      customerId,
                      "USD",
                      customer.getName(),
                      customer.getEmail(),
                      customer.getAddress(),
                      "Test Org",
                      memberIdOwner);
              invoice = invoiceRepository.save(invoice);

              // Create invoice lines
              var line1 =
                  new InvoiceLine(
                      invoice.getId(),
                      projectId,
                      timeEntryId1,
                      "Development work - 4.0 hours",
                      new BigDecimal("4.00"),
                      new BigDecimal("150.00"),
                      new BigDecimal("600.00"),
                      0);
              line1 = invoiceLineRepository.save(line1);

              var line2 =
                  new InvoiceLine(
                      invoice.getId(),
                      projectId,
                      timeEntryId2,
                      "Code review - 2.0 hours",
                      new BigDecimal("2.00"),
                      new BigDecimal("150.00"),
                      new BigDecimal("300.00"),
                      1);
              line2 = invoiceLineRepository.save(line2);

              return new UUID[] {invoice.getId(), line1.getId(), line2.getId()};
            });

    UUID invoiceId = savedIds[0];
    UUID line1Id = savedIds[1];
    UUID line2Id = savedIds[2];

    // Verify retrieval
    runInTenantVoid(
        () -> {
          var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
          assertThat(lines).hasSize(2);

          var line1 = lines.get(0);
          assertThat(line1.getId()).isEqualTo(line1Id);
          assertThat(line1.getDescription()).isEqualTo("Development work - 4.0 hours");
          assertThat(line1.getQuantity()).isEqualByComparingTo(new BigDecimal("4.00"));
          assertThat(line1.getUnitPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
          assertThat(line1.getAmount()).isEqualByComparingTo(new BigDecimal("600.00"));
          assertThat(line1.getTimeEntryId()).isEqualTo(timeEntryId1);

          var line2 = lines.get(1);
          assertThat(line2.getId()).isEqualTo(line2Id);
          assertThat(line2.getTimeEntryId()).isEqualTo(timeEntryId2);
        });
  }

  @Test
  @Order(3)
  void testInvoiceStatusTransitions_draftToApprovedValid() {
    runInTenantVoid(
        () -> {
          Customer customer = customerRepository.findOneById(customerId).orElseThrow();
          var invoice =
              new Invoice(
                  customerId,
                  "USD",
                  customer.getName(),
                  customer.getEmail(),
                  customer.getAddress(),
                  "Test Org",
                  memberIdOwner);
          invoice = invoiceRepository.save(invoice);

          // DRAFT → APPROVED should succeed
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
          assertThat(invoice.canEdit()).isTrue();

          invoice.approve(memberIdOwner);
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
          assertThat(invoice.getApprovedBy()).isEqualTo(memberIdOwner);
          assertThat(invoice.getIssueDate()).isNotNull(); // Auto-set to today
          assertThat(invoice.canEdit()).isFalse();

          invoiceRepository.save(invoice);
        });
  }

  @Test
  @Order(4)
  void testInvoiceStatusTransitions_draftToSentInvalid() {
    runInTenantVoid(
        () -> {
          Customer customer = customerRepository.findOneById(customerId).orElseThrow();
          var invoice =
              new Invoice(
                  customerId,
                  "USD",
                  customer.getName(),
                  customer.getEmail(),
                  customer.getAddress(),
                  "Test Org",
                  memberIdOwner);
          final var savedInvoice = invoiceRepository.save(invoice);

          // DRAFT → SENT should fail
          assertThatThrownBy(() -> savedInvoice.markSent())
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("Cannot mark invoice as sent in status DRAFT");
        });
  }

  @Test
  @Order(5)
  void testInvoiceStatusTransitions_paidTerminal() {
    runInTenantVoid(
        () -> {
          Customer customer = customerRepository.findOneById(customerId).orElseThrow();
          var invoice =
              new Invoice(
                  customerId,
                  "USD",
                  customer.getName(),
                  customer.getEmail(),
                  customer.getAddress(),
                  "Test Org",
                  memberIdOwner);
          final var savedInvoice = invoiceRepository.save(invoice);

          // Transition to PAID: DRAFT → APPROVED → SENT → PAID
          savedInvoice.approve(memberIdOwner);
          savedInvoice.markSent();
          savedInvoice.recordPayment("TXN-12345");

          assertThat(savedInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
          assertThat(savedInvoice.getPaidAt()).isNotNull();
          assertThat(savedInvoice.getPaymentReference()).isEqualTo("TXN-12345");

          // PAID → VOID should fail (terminal state)
          assertThatThrownBy(() -> savedInvoice.voidInvoice())
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("Cannot void invoice in status PAID");
        });
  }

  @Test
  @Order(6)
  void testInvoiceStatusTransitions_voidTerminal() {
    runInTenantVoid(
        () -> {
          Customer customer = customerRepository.findOneById(customerId).orElseThrow();
          var invoice =
              new Invoice(
                  customerId,
                  "USD",
                  customer.getName(),
                  customer.getEmail(),
                  customer.getAddress(),
                  "Test Org",
                  memberIdOwner);
          final var savedInvoice = invoiceRepository.save(invoice);

          // Transition to VOID: DRAFT → APPROVED → VOID
          savedInvoice.approve(memberIdOwner);
          savedInvoice.voidInvoice();

          assertThat(savedInvoice.getStatus()).isEqualTo(InvoiceStatus.VOID);

          // VOID → any transition should fail (terminal state)
          assertThatThrownBy(() -> savedInvoice.markSent())
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("Cannot mark invoice as sent in status VOID");
        });
  }

  @Test
  @Order(7)
  void testInvoiceRecalculateTotals() {
    runInTenantVoid(
        () -> {
          Customer customer = customerRepository.findOneById(customerId).orElseThrow();
          var invoice =
              new Invoice(
                  customerId,
                  "USD",
                  customer.getName(),
                  customer.getEmail(),
                  customer.getAddress(),
                  "Test Org",
                  memberIdOwner);
          invoice = invoiceRepository.save(invoice);

          // Create lines
          var line1 =
              new InvoiceLine(
                  invoice.getId(),
                  projectId,
                  null,
                  "Item 1",
                  new BigDecimal("1.00"),
                  new BigDecimal("100.00"),
                  new BigDecimal("100.00"),
                  0);
          invoiceLineRepository.save(line1);

          var line2 =
              new InvoiceLine(
                  invoice.getId(),
                  projectId,
                  null,
                  "Item 2",
                  new BigDecimal("2.00"),
                  new BigDecimal("50.00"),
                  new BigDecimal("100.00"),
                  1);
          invoiceLineRepository.save(line2);

          // Recalculate totals
          var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
          invoice.recalculateTotals(lines);

          assertThat(invoice.getSubtotal()).isEqualByComparingTo(new BigDecimal("200.00"));
          assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("200.00"));

          // Add tax
          invoice.setTaxAmount(new BigDecimal("20.00"));
          assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("220.00"));

          invoiceRepository.save(invoice);
        });
  }

  @Test
  @Order(8)
  void testInvoiceNumberServiceSequential() throws Exception {
    var numbers =
        runInTenant(
            () -> {
              String num1 = invoiceNumberService.assignInvoiceNumber();
              String num2 = invoiceNumberService.assignInvoiceNumber();
              String num3 = invoiceNumberService.assignInvoiceNumber();
              return new String[] {num1, num2, num3};
            });

    assertThat(numbers[0]).matches("INV-\\d{4}");
    assertThat(numbers[1]).matches("INV-\\d{4}");
    assertThat(numbers[2]).matches("INV-\\d{4}");

    // Extract numbers and verify sequential
    int n1 = Integer.parseInt(numbers[0].substring(4));
    int n2 = Integer.parseInt(numbers[1].substring(4));
    int n3 = Integer.parseInt(numbers[2].substring(4));

    assertThat(n2).isEqualTo(n1 + 1);
    assertThat(n3).isEqualTo(n2 + 1);
  }

  @Test
  @Order(9)
  void testTimeEntryMarkBilledAndUnbilled() {
    runInTenantVoid(
        () -> {
          // Create an invoice to reference
          Customer customer = customerRepository.findOneById(customerId).orElseThrow();
          var invoice =
              new Invoice(
                  customerId,
                  "USD",
                  customer.getName(),
                  customer.getEmail(),
                  customer.getAddress(),
                  "Test Org",
                  memberIdOwner);
          invoice = invoiceRepository.save(invoice);
          UUID testInvoiceId = invoice.getId();

          // Create a new time entry for this test
          var timeEntry =
              timeEntryService.createTimeEntry(
                  taskId, LocalDate.now(), 60, true, null, "Billing test", memberIdOwner, "owner");

          // Verify initially not billed
          timeEntry = timeEntryRepository.findOneById(timeEntry.getId()).orElseThrow();
          assertThat(timeEntry.isBilled()).isFalse();
          assertThat(timeEntry.isLocked()).isFalse();

          // Mark as billed
          timeEntry.markBilled(testInvoiceId);
          timeEntryRepository.save(timeEntry);

          timeEntry = timeEntryRepository.findOneById(timeEntry.getId()).orElseThrow();
          assertThat(timeEntry.isBilled()).isTrue();
          assertThat(timeEntry.isLocked()).isTrue();
          assertThat(timeEntry.getInvoiceId()).isEqualTo(testInvoiceId);

          // Mark as unbilled
          timeEntry.markUnbilled();
          timeEntryRepository.save(timeEntry);

          timeEntry = timeEntryRepository.findOneById(timeEntry.getId()).orElseThrow();
          assertThat(timeEntry.isBilled()).isFalse();
          assertThat(timeEntry.isLocked()).isFalse();
          assertThat(timeEntry.getInvoiceId()).isNull();
        });
  }

  @Test
  @Order(10)
  void testCustomerAddressFieldPersistence() {
    runInTenantVoid(
        () -> {
          var customer = customerRepository.findOneById(customerId).orElseThrow();
          assertThat(customer.getAddress()).isNotNull();
          assertThat(customer.getAddress()).contains("123 Test Street");
          assertThat(customer.getAddress()).contains("Test City, TS 12345");
        });
  }
}
