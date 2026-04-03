package io.b2mash.b2b.b2bstrawman.demo.seed;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Demo data seeder for SA law firm verticals. Creates a realistic legal practice scenario with 5
 * customers, 4+ projects, tasks, time entries, and invoices. Rates range from R1,200/hr (Candidate
 * Attorney) to R3,500/hr (Partner). Legal-specific entities (court dates, adverse parties, tariff
 * items) are only seeded if Phase 55 modules exist; otherwise gracefully skipped.
 */
@Component
public class LegalDemoDataSeeder extends BaseDemoDataSeeder {

  private static final String ORG_NAME = "Demo Legal Practice (Pty) Ltd";

  private static final BigDecimal RATE_CANDIDATE_ATTORNEY = new BigDecimal("1200.00");
  private static final BigDecimal RATE_ASSOCIATE = new BigDecimal("1800.00");
  private static final BigDecimal RATE_SENIOR_ASSOCIATE = new BigDecimal("2500.00");
  private static final BigDecimal RATE_PARTNER = new BigDecimal("3500.00");

  private static final BigDecimal[] RATES = {
    RATE_CANDIDATE_ATTORNEY, RATE_ASSOCIATE, RATE_SENIOR_ASSOCIATE, RATE_PARTNER
  };

  private static final String[] TASK_TITLES = {
    "Due diligence",
    "Contract drafting",
    "FICA verification",
    "Settlement negotiation",
    "Court preparation",
    "Title deed search",
    "Client consultation",
    "Legal opinion",
    "Compliance review",
    "Document discovery"
  };

  private final VerticalModuleRegistry verticalModuleRegistry;

  public LegalDemoDataSeeder(
      TenantTransactionHelper tenantTransactionHelper,
      MemberRepository memberRepository,
      OrgRoleRepository orgRoleRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      ProjectMemberRepository projectMemberRepository,
      VerticalModuleRegistry verticalModuleRegistry) {
    super(
        tenantTransactionHelper,
        memberRepository,
        orgRoleRepository,
        customerRepository,
        projectRepository,
        taskRepository,
        timeEntryRepository,
        invoiceRepository,
        invoiceLineRepository,
        projectMemberRepository);
    this.verticalModuleRegistry = verticalModuleRegistry;
  }

  @Override
  protected void seedProfileData(String schemaName, UUID orgId) {
    Random rand = seededRandom(orgId);

    // 1. Members (4: 1 owner/partner, 1 admin/senior associate, 2 members/associates)
    List<UUID> ownerIds = createMembers("owner", 1, 20);
    List<UUID> adminIds = createMembers("admin", 1, 21);
    List<UUID> memberIds = createMembers("member", 2, 22);

    List<UUID> allMemberIds = new ArrayList<>();
    allMemberIds.addAll(ownerIds);
    allMemberIds.addAll(adminIds);
    allMemberIds.addAll(memberIds);

    UUID primaryMemberId = ownerIds.getFirst();

    // 2. Customers (5, mix of lifecycle statuses)
    List<CustomerInfo> customers = createCustomers(primaryMemberId);

    // 3. Projects (linked to ACTIVE customers)
    List<ProjectInfo> projects = createProjects(customers, primaryMemberId, rand);

    // 4. ProjectMembers (all 4 members on each project)
    for (ProjectInfo project : projects) {
      for (UUID memberId : allMemberIds) {
        var pm = new ProjectMember(project.id(), memberId, "CONTRIBUTOR", primaryMemberId);
        projectMemberRepository.save(pm);
      }
    }

    // 5. Tasks (50, distributed across active projects with assignees)
    List<UUID> taskIds = createTasks(projects, allMemberIds, primaryMemberId, rand);

    // 6. Time entries (200, distributed across 90 days)
    List<TimeEntry> timeEntries =
        createTimeEntries(taskIds, allMemberIds, 200, 90, true, RATES, rand);

    // 7. Invoices (10) from unbilled time entries
    createInvoices(customers, timeEntries, primaryMemberId, rand);

    // 8. Legal-specific entities (court dates, adverse parties, tariff items)
    seedLegalSpecificEntities();

    log.info(
        "Legal demo data seeded: {} members, {} customers, {} projects, {} tasks, {} time entries",
        allMemberIds.size(),
        customers.size(),
        projects.size(),
        taskIds.size(),
        timeEntries.size());
  }

  /**
   * Attempts to seed legal-specific entities (court dates, adverse parties, tariff items). Checks
   * for the existence of Phase 55 module infrastructure and gracefully skips if not available.
   */
  private void seedLegalSpecificEntities() {
    boolean courtCalendarRegistered =
        verticalModuleRegistry.getModule("court_calendar").isPresent();

    if (courtCalendarRegistered) {
      // The court_calendar module is registered but the actual entity infrastructure
      // (court date entities, repositories) does not yet exist in the codebase.
      // When Phase 55 adds the entity layer, this block can be extended to seed
      // court dates, adverse parties, and tariff items.
      log.info(
          "Court calendar module registered but entity infrastructure not yet available"
              + " -- skipping legal-specific entities");
    } else {
      log.info("Court calendar module not registered -- skipping legal-specific entities");
    }
  }

  private List<CustomerInfo> createCustomers(UUID createdBy) {
    record CustomerSpec(String name, String email, String phone, LifecycleStatus status) {}

    List<CustomerSpec> specs =
        List.of(
            new CustomerSpec(
                "Dlamini Property Trust",
                "admin@dlaminitrust.co.za",
                "+27 11 555 0201",
                LifecycleStatus.ACTIVE),
            new CustomerSpec(
                "Naidoo & Partners Developers",
                "legal@naidoopartners.co.za",
                "+27 31 555 0202",
                LifecycleStatus.ACTIVE),
            new CustomerSpec(
                "Botha Family Estate",
                "estate@bothafamily.co.za",
                "+27 12 555 0203",
                LifecycleStatus.ACTIVE),
            new CustomerSpec(
                "Msimang Transport (Pty) Ltd",
                "operations@msimangtransport.co.za",
                "+27 11 555 0204",
                LifecycleStatus.ONBOARDING),
            new CustomerSpec(
                "Cele Holdings",
                "info@celeholdings.co.za",
                "+27 31 555 0205",
                LifecycleStatus.PROSPECT));

    List<CustomerInfo> results = new ArrayList<>();
    for (CustomerSpec spec : specs) {
      Customer customer =
          new Customer(
              spec.name(),
              spec.email(),
              spec.phone(),
              null,
              null,
              createdBy,
              CustomerType.COMPANY,
              spec.status());
      customer = customerRepository.save(customer);
      results.add(new CustomerInfo(customer.getId(), spec.name(), spec.email(), spec.status()));
    }
    return results;
  }

  private List<ProjectInfo> createProjects(
      List<CustomerInfo> customers, UUID createdBy, Random rand) {
    // Only link projects to ACTIVE customers
    List<CustomerInfo> activeCustomers =
        customers.stream().filter(c -> c.status() == LifecycleStatus.ACTIVE).toList();

    record ProjectSpec(String name, String description, boolean completed) {}

    // Projects ordered so names align with modulo customer assignment (i % 3):
    // i=0 -> Dlamini, i=1 -> Naidoo, i=2 -> Botha, i=3 -> Dlamini, etc.
    List<ProjectSpec> specs =
        List.of(
            new ProjectSpec(
                "Dlamini -- Property Transfer DE-2026-001",
                "Conveyancing matter for property transfer in Sandton",
                false),
            new ProjectSpec(
                "Naidoo -- Commercial Lease Review",
                "Review and negotiation of commercial lease agreements for new development",
                false),
            new ProjectSpec(
                "Botha -- Estate Administration",
                "Administration of deceased estate including asset distribution",
                false),
            new ProjectSpec(
                "Dlamini -- Labour Dispute",
                "Labour dispute representation at CCMA and Labour Court",
                false),
            new ProjectSpec(
                "Naidoo -- Sectional Title Registration",
                "Sectional title registration for residential complex",
                true),
            new ProjectSpec(
                "Botha -- Company Formation",
                "CIPC registration and shareholder agreement drafting",
                true));

    List<ProjectInfo> results = new ArrayList<>();
    for (int i = 0; i < specs.size(); i++) {
      ProjectSpec spec = specs.get(i);
      CustomerInfo customer = activeCustomers.get(i % activeCustomers.size());

      Project project = new Project(spec.name(), spec.description(), createdBy);
      project.setCustomerId(customer.id());
      project.setDueDate(daysAgoDate(-rand.nextInt(30))); // due date in the future

      project = projectRepository.save(project);

      if (spec.completed()) {
        project.complete(createdBy);
        project = projectRepository.save(project);
      }

      results.add(new ProjectInfo(project.getId(), spec.name(), spec.completed()));
    }
    return results;
  }

  private List<UUID> createTasks(
      List<ProjectInfo> projects, List<UUID> memberIds, UUID createdBy, Random rand) {
    // Only create tasks for non-completed projects (active ones)
    List<ProjectInfo> activeProjects = projects.stream().filter(p -> !p.completed()).toList();

    String[] priorities = {"LOW", "MEDIUM", "HIGH", "URGENT"};
    List<UUID> allTaskIds = new ArrayList<>();
    int tasksPerProject = 50 / activeProjects.size();
    int remainder = 50 - (tasksPerProject * activeProjects.size());

    for (int p = 0; p < activeProjects.size(); p++) {
      ProjectInfo project = activeProjects.get(p);
      int count = tasksPerProject + (p < remainder ? 1 : 0);

      for (int t = 0; t < count; t++) {
        String title = TASK_TITLES[rand.nextInt(TASK_TITLES.length)];
        String priority = priorities[rand.nextInt(priorities.length)];
        UUID assignee = memberIds.get(rand.nextInt(memberIds.size()));

        Task task =
            new Task(
                project.id(),
                title,
                "Legal task for " + project.name(),
                priority,
                null,
                null,
                createdBy);

        // Mix of statuses: ~40% OPEN, ~40% IN_PROGRESS, ~20% DONE
        int statusRoll = rand.nextInt(10);
        task = taskRepository.save(task);
        if (statusRoll < 4) {
          // Keep OPEN -- assign via update
          task.update(
              task.getTitle(),
              task.getDescription(),
              task.getPriority(),
              task.getStatus(),
              task.getType(),
              task.getDueDate(),
              assignee,
              createdBy,
              null,
              null);
        } else if (statusRoll < 8) {
          // IN_PROGRESS
          task.claim(assignee);
        } else {
          // DONE: must go OPEN -> IN_PROGRESS -> DONE
          task.claim(assignee);
          task = taskRepository.save(task);
          task.complete(assignee);
        }
        task = taskRepository.save(task);
        allTaskIds.add(task.getId());
      }
    }

    // Also create a few tasks for completed projects (all DONE)
    List<ProjectInfo> completedProjects = projects.stream().filter(ProjectInfo::completed).toList();
    for (ProjectInfo project : completedProjects) {
      for (int t = 0; t < 3; t++) {
        String title = TASK_TITLES[rand.nextInt(TASK_TITLES.length)];
        UUID assignee = memberIds.get(rand.nextInt(memberIds.size()));
        Task task =
            new Task(project.id(), title, "Completed legal task", "MEDIUM", null, null, createdBy);
        task = taskRepository.save(task);
        task.claim(assignee);
        task = taskRepository.save(task);
        task.complete(assignee);
        task = taskRepository.save(task);
        allTaskIds.add(task.getId());
      }
    }

    return allTaskIds;
  }

  private void createInvoices(
      List<CustomerInfo> customers, List<TimeEntry> timeEntries, UUID createdBy, Random rand) {
    // Only invoice ACTIVE customers
    List<CustomerInfo> activeCustomers =
        customers.stream().filter(c -> c.status() == LifecycleStatus.ACTIVE).toList();

    // Split time entries into groups for 10 invoices
    List<TimeEntry> unbilled = timeEntries.stream().filter(e -> e.getInvoiceId() == null).toList();
    int entriesPerInvoice = Math.max(1, unbilled.size() / 10);

    // Invoice status distribution: 2 DRAFT, 4 SENT, 3 PAID, 1 VOID
    String[] statusTargets = {
      "DRAFT", "DRAFT", "SENT", "SENT", "SENT", "SENT", "PAID", "PAID", "PAID", "VOID"
    };

    int invoiceCount = 0;
    for (int i = 0; i < 10; i++) {
      int start = i * entriesPerInvoice;
      int end = (i == 9) ? unbilled.size() : start + entriesPerInvoice;
      if (start >= unbilled.size()) break;

      List<TimeEntry> batch = new ArrayList<>(unbilled.subList(start, end));
      if (batch.isEmpty()) continue;

      CustomerInfo customer = activeCustomers.get(i % activeCustomers.size());
      invoiceCount++;
      String invoiceNumber = "LEG-2026-%03d".formatted(invoiceCount);
      String targetStatus = statusTargets[i];

      UUID invoiceId =
          createInvoiceFromTimeEntries(
              customer.id(), customer.name(), customer.email(), batch, ORG_NAME, createdBy);

      // Transition invoice to target status (starts as DRAFT)
      if (!"DRAFT".equals(targetStatus)) {
        var invoice =
            invoiceRepository
                .findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice not found: " + invoiceId));

        // All non-DRAFT statuses need approval first
        invoice.approve(invoiceNumber, createdBy);

        switch (targetStatus) {
          case "SENT" -> invoice.markSent();
          case "PAID" -> {
            invoice.markSent();
            invoice.recordPayment("PAY-LEG-%03d".formatted(invoiceCount));
          }
          case "VOID" -> {
            invoice.markSent();
            invoiceRepository.save(invoice);
            invoice.voidInvoice();
          }
          default -> {}
        }
        invoiceRepository.save(invoice);
      }
    }
  }
}
