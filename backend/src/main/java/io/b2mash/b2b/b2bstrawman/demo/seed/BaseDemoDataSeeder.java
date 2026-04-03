package io.b2mash.b2b.b2bstrawman.demo.seed;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for profile-specific demo data seeders. Provides shared utilities for date
 * computation, member creation, time entry distribution, and invoice generation.
 */
public abstract class BaseDemoDataSeeder {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected final TenantTransactionHelper tenantTransactionHelper;
  protected final MemberRepository memberRepository;
  protected final OrgRoleRepository orgRoleRepository;
  protected final CustomerRepository customerRepository;
  protected final ProjectRepository projectRepository;
  protected final TaskRepository taskRepository;
  protected final TimeEntryRepository timeEntryRepository;
  protected final InvoiceRepository invoiceRepository;
  protected final InvoiceLineRepository invoiceLineRepository;
  protected final ProjectMemberRepository projectMemberRepository;

  protected BaseDemoDataSeeder(
      TenantTransactionHelper tenantTransactionHelper,
      MemberRepository memberRepository,
      OrgRoleRepository orgRoleRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      ProjectMemberRepository projectMemberRepository) {
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.memberRepository = memberRepository;
    this.orgRoleRepository = orgRoleRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.projectMemberRepository = projectMemberRepository;
  }

  /**
   * Public entry point. Runs the seeder inside a tenant transaction for the given schema.
   *
   * @param schemaName tenant schema name (e.g. "tenant_a1b2c3d4e5f6")
   * @param orgId the organization UUID
   */
  public void seed(String schemaName, UUID orgId) {
    log.info("Seeding demo data for schema {} (org {})", schemaName, orgId);
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName, orgId.toString(), t -> seedProfileData(schemaName, orgId));
    log.info("Demo data seeding complete for schema {}", schemaName);
  }

  /**
   * Profile-specific seeding logic. Implementations create customers, projects, tasks, time
   * entries, and invoices appropriate for their vertical.
   */
  protected abstract void seedProfileData(String schemaName, UUID orgId);

  // --- Date utilities ---

  protected Instant daysAgo(int n) {
    return Instant.now().minus(Duration.ofDays(n));
  }

  protected LocalDate daysAgoDate(int n) {
    return LocalDate.now().minusDays(n);
  }

  protected Instant monthsAgo(int n) {
    return Instant.now().minus(Duration.ofDays(n * 30L));
  }

  // --- Deterministic random ---

  protected Random seededRandom(UUID orgId) {
    return new Random(orgId.getLeastSignificantBits() ^ orgId.getMostSignificantBits());
  }

  // --- Member creation ---

  /**
   * Creates demo members in the tenant schema with fake Keycloak user IDs. Returns the list of
   * created member UUIDs.
   *
   * @param roleSlug the org role slug ("owner", "admin", or "member")
   * @param count number of members to create
   * @param startIndex starting index for deterministic naming
   * @return list of saved member UUIDs
   */
  protected List<UUID> createMembers(String roleSlug, int count, int startIndex) {
    OrgRole role =
        orgRoleRepository
            .findBySlug(roleSlug)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "OrgRole '%s' not found in tenant schema".formatted(roleSlug)));

    List<UUID> memberIds = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int idx = startIndex + i;
      String fakeName = "Demo %s %d".formatted(capitalize(roleSlug), idx);
      Member member =
          new Member(
              "demo-member-" + idx,
              "demo-%s-%d@example.com".formatted(roleSlug, idx),
              fakeName,
              null,
              role);
      member = memberRepository.save(member);
      memberIds.add(member.getId());
    }
    return memberIds;
  }

  // --- Time entry distribution ---

  /**
   * Creates time entries distributed across tasks, members, and days. Each entry gets a billing
   * rate snapshot if billable.
   *
   * @param taskIds list of task UUIDs to distribute entries across
   * @param memberIds list of member UUIDs to distribute entries across
   * @param totalEntries total number of time entries to create
   * @param daysBack maximum days in the past for entry dates
   * @param billable whether entries are billable
   * @param rates array of billing rates to cycle through (ZAR per hour)
   * @param rand seeded Random for deterministic distribution
   * @return list of saved TimeEntry entities
   */
  protected List<TimeEntry> createTimeEntries(
      List<UUID> taskIds,
      List<UUID> memberIds,
      int totalEntries,
      int daysBack,
      boolean billable,
      BigDecimal[] rates,
      Random rand) {
    List<TimeEntry> entries = new ArrayList<>();
    for (int i = 0; i < totalEntries; i++) {
      UUID taskId = taskIds.get(rand.nextInt(taskIds.size()));
      UUID memberId = memberIds.get(rand.nextInt(memberIds.size()));
      LocalDate date = daysAgoDate(rand.nextInt(daysBack));
      int durationMinutes = 60 + rand.nextInt(421); // 60-480 minutes (1-8h)
      BigDecimal rate = rates[rand.nextInt(rates.length)];

      TimeEntry entry =
          new TimeEntry(taskId, memberId, date, durationMinutes, billable, null, "Demo work entry");
      if (billable) {
        entry.snapshotBillingRate(rate, "ZAR");
      }
      entries.add(entry);
    }
    return timeEntryRepository.saveAll(entries);
  }

  // --- Invoice generation from time entries ---

  /**
   * Creates a DRAFT invoice from unbilled time entries, generates invoice lines, marks entries as
   * billed, and applies 15% VAT. The invoice is left in DRAFT state — callers should transition it
   * to the desired status (APPROVED, SENT, PAID, VOID) as needed.
   *
   * @param customerId the customer UUID
   * @param customerName the customer display name
   * @param customerEmail the customer email
   * @param timeEntries list of unbilled TimeEntry entities
   * @param orgName the organization name for the invoice
   * @param createdBy the member UUID who created the invoice
   * @return the saved Invoice UUID (in DRAFT status)
   */
  protected UUID createInvoiceFromTimeEntries(
      UUID customerId,
      String customerName,
      String customerEmail,
      List<TimeEntry> timeEntries,
      String orgName,
      UUID createdBy) {
    Invoice invoice =
        new Invoice(customerId, "ZAR", customerName, customerEmail, null, orgName, createdBy);
    invoice = invoiceRepository.save(invoice);

    BigDecimal subtotal = BigDecimal.ZERO;
    int sortOrder = 0;
    for (TimeEntry entry : timeEntries) {
      BigDecimal hours =
          new BigDecimal(entry.getDurationMinutes())
              .divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);
      BigDecimal unitPrice =
          entry.getBillingRateSnapshot() != null
              ? entry.getBillingRateSnapshot()
              : new BigDecimal("1100.00");

      InvoiceLine line =
          new InvoiceLine(
              invoice.getId(),
              null, // projectId could be resolved but not required for demo
              entry.getId(),
              "Time entry: %d min".formatted(entry.getDurationMinutes()),
              hours,
              unitPrice,
              sortOrder++);
      line.setLineSource("TIME");
      invoiceLineRepository.save(line);

      subtotal = subtotal.add(line.getAmount());

      entry.setInvoiceId(invoice.getId());
    }
    timeEntryRepository.saveAll(timeEntries);

    BigDecimal taxAmount =
        subtotal.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
    // updateDraft sets taxAmount and recalculates total = subtotal + taxAmount
    invoice.recalculateTotals(subtotal, false, BigDecimal.ZERO, false);
    invoice.updateDraft(null, null, null, taxAmount);
    invoiceRepository.save(invoice);

    return invoice.getId();
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  /** Shared record for tracking customer data during seeding. */
  protected record CustomerInfo(UUID id, String name, String email, LifecycleStatus status) {}

  /** Shared record for tracking project data during seeding. */
  protected record ProjectInfo(UUID id, String name, boolean completed) {}
}
