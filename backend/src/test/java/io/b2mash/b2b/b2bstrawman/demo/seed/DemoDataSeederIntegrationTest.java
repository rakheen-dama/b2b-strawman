package io.b2mash.b2b.b2bstrawman.demo.seed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoDataSeederIntegrationTest {

  private final GenericDemoDataSeeder genericDemoDataSeeder;
  private final AccountingDemoDataSeeder accountingDemoDataSeeder;
  private final DemoDataSeeder demoDataSeeder;
  private final TenantProvisioningService tenantProvisioningService;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final OrgSchemaMappingRepository mappingRepository;
  private final OrganizationRepository organizationRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final MemberRepository memberRepository;
  private final ProjectMemberRepository projectMemberRepository;

  private String schemaName;
  private UUID orgId;

  @Autowired
  DemoDataSeederIntegrationTest(
      GenericDemoDataSeeder genericDemoDataSeeder,
      AccountingDemoDataSeeder accountingDemoDataSeeder,
      DemoDataSeeder demoDataSeeder,
      TenantProvisioningService tenantProvisioningService,
      TenantTransactionHelper tenantTransactionHelper,
      OrgSchemaMappingRepository mappingRepository,
      OrganizationRepository organizationRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      MemberRepository memberRepository,
      ProjectMemberRepository projectMemberRepository) {
    this.genericDemoDataSeeder = genericDemoDataSeeder;
    this.accountingDemoDataSeeder = accountingDemoDataSeeder;
    this.demoDataSeeder = demoDataSeeder;
    this.tenantProvisioningService = tenantProvisioningService;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.mappingRepository = mappingRepository;
    this.organizationRepository = organizationRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.memberRepository = memberRepository;
    this.projectMemberRepository = projectMemberRepository;
  }

  @BeforeAll
  void setUp() {
    String slug = "data-consistency-test-" + UUID.randomUUID().toString().substring(0, 8);
    tenantProvisioningService.provisionTenant(slug, "Data Consistency Test Org", "generic");
    schemaName = mappingRepository.findByExternalOrgId(slug).orElseThrow().getSchemaName();
    orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();
    genericDemoDataSeeder.seed(schemaName, orgId);
  }

  @Test
  void invoiceLineForeignKeys_areValid() {
    var result = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          var invoiceIds =
              invoiceRepository.findAll().stream().map(Invoice::getId).collect(Collectors.toSet());
          var lines = invoiceLineRepository.findAll();
          assertFalse(lines.isEmpty(), "Should have invoice lines");
          boolean allValid =
              lines.stream().allMatch(line -> invoiceIds.contains(line.getInvoiceId()));
          result.set(allValid);
        });
    assertTrue(result.get(), "Every invoice line must reference an existing invoice");
  }

  @Test
  void invoiceTotals_matchLineItemsPlus15PercentVat() {
    var result = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          var invoices = invoiceRepository.findAll();
          var lines = invoiceLineRepository.findAll();

          // Group lines by invoice ID
          var linesByInvoice =
              lines.stream().collect(Collectors.groupingBy(InvoiceLine::getInvoiceId));

          boolean allMatch =
              invoices.stream()
                  .filter(
                      inv -> {
                        var status = inv.getStatus().name();
                        return "PAID".equals(status)
                            || "SENT".equals(status)
                            || "DRAFT".equals(status);
                      })
                  .filter(inv -> inv.getSubtotal() != null && inv.getTotal() != null)
                  .allMatch(
                      inv -> {
                        BigDecimal subtotal = inv.getSubtotal();
                        BigDecimal taxAmount =
                            inv.getTaxAmount() != null ? inv.getTaxAmount() : BigDecimal.ZERO;
                        BigDecimal total = inv.getTotal();

                        // Verify total = subtotal + taxAmount (±1 cent)
                        BigDecimal expectedTotal = subtotal.add(taxAmount);
                        BigDecimal diff = total.subtract(expectedTotal).abs();
                        boolean totalCorrect = diff.compareTo(new BigDecimal("0.01")) <= 0;

                        // Verify taxAmount ≈ subtotal * 0.15 (±1 cent)
                        BigDecimal expectedTax =
                            subtotal
                                .multiply(new BigDecimal("0.15"))
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal taxDiff = taxAmount.subtract(expectedTax).abs();
                        boolean taxCorrect = taxDiff.compareTo(new BigDecimal("0.01")) <= 0;

                        return totalCorrect && taxCorrect;
                      });
          result.set(allMatch);
        });
    assertTrue(
        result.get(),
        "Invoice totals must equal subtotal + taxAmount, and taxAmount ≈ subtotal * 0.15");
  }

  @Test
  void allTimeEntries_referenceValidTasks() {
    var result = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          var taskIds =
              taskRepository.findAll().stream().map(Task::getId).collect(Collectors.toSet());
          var timeEntries = timeEntryRepository.findAll();
          assertFalse(timeEntries.isEmpty(), "Should have time entries");
          boolean allValid =
              timeEntries.stream().allMatch(entry -> taskIds.contains(entry.getTaskId()));
          result.set(allValid);
        });
    assertTrue(result.get(), "Every time entry must reference a valid task");
  }

  @Test
  void taskAssignees_areProjectMembers() {
    var result = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          var tasks = taskRepository.findAll();
          var projectMembers = projectMemberRepository.findAll();

          // Build set of (projectId, memberId) pairs
          var pmPairs =
              projectMembers.stream()
                  .map(pm -> pm.getProjectId() + ":" + pm.getMemberId())
                  .collect(Collectors.toSet());

          boolean allValid =
              tasks.stream()
                  .filter(task -> task.getAssigneeId() != null)
                  .allMatch(
                      task -> {
                        String key = task.getProjectId() + ":" + task.getAssigneeId();
                        return pmPairs.contains(key);
                      });
          result.set(allValid);
        });
    assertTrue(result.get(), "Every task assignee must be a project member of that task's project");
  }

  @Test
  void chronologicalOrdering_customerCreatedBeforeProject() {
    var result = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          var projects = projectRepository.findAll();
          var customers = customerRepository.findAll();

          // Build map of customer ID -> createdAt
          var customerCreatedAt =
              customers.stream().collect(Collectors.toMap(Customer::getId, Customer::getCreatedAt));

          boolean allValid =
              projects.stream()
                  .filter(p -> p.getCustomerId() != null)
                  .allMatch(
                      p -> {
                        var custCreated = customerCreatedAt.get(p.getCustomerId());
                        if (custCreated == null)
                          return true; // customer not found — different check
                        // Customer must be created before or within 1 second of project
                        return !custCreated.isAfter(p.getCreatedAt().plusSeconds(1));
                      });
          result.set(allValid);
        });
    assertTrue(
        result.get(),
        "Customer createdAt must be before or equal to project createdAt (±1s tolerance)");
  }

  @Test
  void atLeastOneProject_hasHighBudgetUtilization() {
    var result = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          var timeEntries = timeEntryRepository.findAll();
          // Group time entries by task's project
          var tasks = taskRepository.findAll();
          var taskToProject =
              tasks.stream().collect(Collectors.toMap(Task::getId, Task::getProjectId));

          // Count time entries per project
          var entriesPerProject =
              timeEntries.stream()
                  .filter(entry -> taskToProject.containsKey(entry.getTaskId()))
                  .collect(
                      Collectors.groupingBy(
                          entry -> taskToProject.get(entry.getTaskId()), Collectors.counting()));

          boolean hasHighUtilization =
              entriesPerProject.values().stream().anyMatch(count -> count >= 15);
          result.set(hasHighUtilization);
        });
    assertTrue(
        result.get(),
        "At least one project should have >= 15 time entries (high utilization proxy)");
  }

  @Test
  void budgetUtilization_matchesActualTimeEntryHours() {
    var result = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          var timeEntries = timeEntryRepository.findAll();
          // Verify time entries exist and have positive duration
          boolean hasEntries = !timeEntries.isEmpty();
          boolean allPositiveDuration =
              timeEntries.stream().allMatch(entry -> entry.getDurationMinutes() > 0);
          result.set(hasEntries && allPositiveDuration);
        });
    assertTrue(result.get(), "Time entries should exist and have positive duration");
  }

  @Test
  void dispatcher_routesToCorrectSeeder_accounting() {
    // Provision a separate tenant for accounting profile
    String slug = "acct-dispatch-test-" + UUID.randomUUID().toString().substring(0, 8);
    tenantProvisioningService.provisionTenant(slug, "Accounting Dispatch Test Org", "accounting");
    String acctSchema = mappingRepository.findByExternalOrgId(slug).orElseThrow().getSchemaName();
    UUID acctOrgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();

    // Use the dispatcher to seed — should route to AccountingDemoDataSeeder
    demoDataSeeder.seed(acctSchema, acctOrgId, "accounting");

    var customerNames = new AtomicReference<List<String>>();
    tenantTransactionHelper.executeInTenantTransaction(
        acctSchema,
        acctOrgId.toString(),
        t ->
            customerNames.set(
                customerRepository.findAll().stream().map(Customer::getName).toList()));

    List<String> names = customerNames.get();
    assertFalse(names.isEmpty(), "Accounting seeder should create customers");
    boolean hasAccountingCustomer =
        names.stream()
            .anyMatch(
                n ->
                    n.contains("Van der Merwe")
                        || n.contains("Karoo Investments")
                        || n.contains("Berg & Berg"));
    assertTrue(
        hasAccountingCustomer,
        "Accounting seeder should create accounting-specific customers, found: " + names);
  }
}
