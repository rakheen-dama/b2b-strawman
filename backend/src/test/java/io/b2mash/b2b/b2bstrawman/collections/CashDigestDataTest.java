package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 593A.3 — deterministic assembly proof for the weekly cash digest. Seeds a controlled book (SENT
 * invoices at fixed days-overdue for each aging bucket; invoices straddling the 7-day billed and
 * collected window edges; unbilled time entries older/younger than 30 days; chase activities in
 * several statuses) and asserts {@link CashDigestService#assembleData()} returns exactly the right
 * numbers — no AI, no email, no delivery. Also asserts the {@code cash_digest} handler is
 * registered.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashDigestDataTest {

  private static final String ORG_ID = "org_cash_digest_data_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private CashDigestService cashDigestService;
  @Autowired private CashDigestHandler cashDigestHandler;
  @Autowired private List<JobHandler> jobHandlers;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Cash Digest Data Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_cd_data", "cd_data@test.com", "Digest Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    runInTenant(this::seedBook);
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  /**
   * Seeds the book. SENT invoices land in outstanding + their aging bucket; PAID invoices only feed
   * billed/collected. issue_date and paid_at are pinned via JDBC so the trailing-window edges are
   * exact.
   */
  private void seedBook() {
    LocalDate today = LocalDate.now();

    // ── Aging buckets (SENT). issue_date backdated out of the billed window so they don't inflate
    // billed. Each has its own customer so the debtor book yields distinct top-risk rows. ──
    UUID agingCurrent =
        seedSentInvoice("Digest Aging Current", 100, today.plusDays(5), today.minusDays(30));
    UUID agingD30 =
        seedSentInvoice("Digest Aging D30", 200, today.minusDays(10), today.minusDays(30));
    UUID agingD60 =
        seedSentInvoice("Digest Aging D60", 400, today.minusDays(45), today.minusDays(30));
    UUID agingD90 =
        seedSentInvoice("Digest Aging D90", 800, today.minusDays(100), today.minusDays(30));

    // ── Billed window edges (SENT). Both current bucket. ──
    // In window (issue today-3): counts toward billed.
    seedSentInvoice("Digest Billed In", 1000, today.plusDays(20), today.minusDays(3));
    // Out of window (issue today-10): NOT counted; the biggest outstanding, so the top debtor.
    seedSentInvoice("Big Debtor Co", 5000, today.plusDays(20), today.minusDays(10));

    // ── Collected / billed window edges (PAID; PAID rows never hit outstanding). ──
    // Paid in window (collected), issued out of window (not billed).
    seedPaidInvoice("Digest Paid In", 700, today.minusDays(10), instantDaysAgo(2));
    // Paid out of window (not collected), issued out of window (not billed).
    seedPaidInvoice("Digest Paid Out", 900, today.minusDays(10), instantDaysAgo(20));
    // Issued in window (billed includes PAID), paid out of window (not collected).
    seedPaidInvoice("Digest Paid Billed", 300, today.minusDays(2), instantDaysAgo(20));

    // ── Stale unbilled WIP: billable, no invoice, worked more than 30 days ago. ──
    Project project = projectRepository.save(new Project("Digest WIP Project", "wip", memberId));
    Task task =
        taskRepository.save(
            new Task(project.getId(), "WIP Task", "wip", "MEDIUM", "TASK", null, memberId));
    saveTimeEntry(task.getId(), today.minusDays(40), 120, true); // stale, counts
    saveTimeEntry(task.getId(), today.minusDays(60), 180, true); // stale, counts
    saveTimeEntry(task.getId(), today.minusDays(10), 240, true); // recent, excluded
    saveTimeEntry(task.getId(), today.minusDays(50), 300, false); // non-billable, excluded

    // ── Chase activities in the trailing window (updated_at = now on insert). One STAGE_1 row per
    // invoice — the (invoice, stage) pair is uniquely constrained. Counts are status-only. ──
    saveActivity(agingCurrent, CollectionActivityStatus.SENT);
    saveActivity(agingD30, CollectionActivityStatus.SENT);
    saveActivity(agingD60, CollectionActivityStatus.PROPOSED);
    saveActivity(agingD90, CollectionActivityStatus.SKIPPED);
  }

  private UUID seedSentInvoice(String name, long total, LocalDate dueDate, LocalDate issueDate) {
    UUID invoiceId = seedInvoice(name, total, dueDate, false, null);
    pinIssueDate(invoiceId, issueDate);
    return invoiceId;
  }

  private void seedPaidInvoice(String name, long total, LocalDate issueDate, Instant paidAt) {
    UUID invoiceId = seedInvoice(name, total, LocalDate.now().minusDays(5), true, paidAt);
    pinIssueDate(invoiceId, issueDate);
    jdbcTemplate.update(
        "UPDATE \"%s\".invoices SET paid_at = ? WHERE id = ?::uuid".formatted(tenantSchema),
        Timestamp.from(paidAt),
        invoiceId.toString());
  }

  private UUID seedInvoice(
      String name, long total, LocalDate dueDate, boolean paid, Instant paidAt) {
    Customer customer =
        customerRepository.save(
            TestCustomerFactory.createActiveCustomer(
                name, name.replace(' ', '_') + "@test.com", memberId));
    var invoice =
        new Invoice(customer.getId(), "ZAR", name, "x@test.com", null, "Test Org", memberId);
    invoice.updateDraft(dueDate, null, null, BigDecimal.ZERO);
    invoice.recalculateTotals(BigDecimal.valueOf(total), false, BigDecimal.ZERO, false);
    invoice.approve("INV-" + UUID.randomUUID().toString().substring(0, 8), memberId);
    invoice.markSent();
    if (paid) {
      invoice.recordPayment("ref-" + name);
    }
    return invoiceRepository.save(invoice).getId();
  }

  private void pinIssueDate(UUID invoiceId, LocalDate issueDate) {
    jdbcTemplate.update(
        "UPDATE \"%s\".invoices SET issue_date = ? WHERE id = ?::uuid".formatted(tenantSchema),
        Date.valueOf(issueDate),
        invoiceId.toString());
  }

  private void saveTimeEntry(UUID taskId, LocalDate date, int minutes, boolean billable) {
    timeEntryRepository.save(new TimeEntry(taskId, memberId, date, minutes, billable, null, "wip"));
  }

  private void saveActivity(UUID invoiceId, CollectionActivityStatus status) {
    Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
    activityRepository.save(
        new CollectionActivity(
            invoiceId, invoice.getCustomerId(), CollectionStage.STAGE_1, status, 10, null));
  }

  private static Instant instantDaysAgo(int days) {
    return Instant.now().minus(days, ChronoUnit.DAYS);
  }

  @Test
  void assembleData_computesNumbersDeterministically() {
    CashDigestData[] holder = new CashDigestData[1];
    runInTenant(() -> holder[0] = cashDigestService.assembleData());
    CashDigestData data = holder[0];

    // Outstanding = every SENT invoice: 100+200+400+800+1000+5000.
    assertThat(data.outstandingTotal()).isEqualByComparingTo("7500");
    // Buckets: current = 100 (aging current) + 1000 (billed-in) + 5000 (big debtor).
    assertThat(data.buckets().current()).isEqualByComparingTo("6100");
    assertThat(data.buckets().d30()).isEqualByComparingTo("200");
    assertThat(data.buckets().d60()).isEqualByComparingTo("400");
    assertThat(data.buckets().d90plus()).isEqualByComparingTo("800");

    // Billed (issue_date in last 7 days, status SENT|PAID): 1000 (SENT in-window) + 300 (PAID
    // in-window). The 5000 SENT was issued out of window; PAID-out/collected rows are out too.
    assertThat(data.billed()).isEqualByComparingTo("1300");
    // Collected (paid_at in last 7 days): 700 only.
    assertThat(data.collected()).isEqualByComparingTo("700");

    // Stale WIP: two billable, uninvoiced entries older than 30 days (120+180 min = 5.0 h).
    assertThat(data.staleWipEntryCount()).isEqualTo(2L);
    assertThat(data.staleWipHours()).isCloseTo(5.0, within(0.001));

    // Activity counts by status over the trailing window.
    assertThat(data.activityCountsByStatus())
        .containsEntry("SENT", 2L)
        .containsEntry("PROPOSED", 1L)
        .containsEntry("SKIPPED", 1L);

    // Top debtor risks: six SENT customers, capped at five, ordered by outstanding DESC.
    assertThat(data.topRisks()).hasSize(5);
    assertThat(data.topRisks().get(0).customerName()).isEqualTo("Big Debtor Co");
    assertThat(data.topRisks().get(0).outstanding()).isEqualByComparingTo("5000");
  }

  @Test
  void cashDigestHandler_isRegisteredWithCashDigestJobType() {
    assertThat(cashDigestHandler.jobType()).isEqualTo("cash_digest");
    assertThat(jobHandlers)
        .anyMatch(h -> "cash_digest".equals(h.jobType()) && h instanceof CashDigestHandler);
  }
}
