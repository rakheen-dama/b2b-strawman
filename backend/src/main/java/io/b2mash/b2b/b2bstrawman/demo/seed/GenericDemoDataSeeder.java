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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Demo data seeder for generic agency/consultancy verticals. Creates a realistic marketing agency
 * scenario with 5 customers, 8 projects, ~50 tasks, ~200 time entries, and 10 invoices.
 */
@Component
public class GenericDemoDataSeeder extends BaseDemoDataSeeder {

  private static final String ORG_NAME = "Demo Agency (Pty) Ltd";

  private static final BigDecimal RATE_JUNIOR = new BigDecimal("850.00");
  private static final BigDecimal RATE_MID = new BigDecimal("1100.00");
  private static final BigDecimal RATE_SENIOR = new BigDecimal("1350.00");
  private static final BigDecimal RATE_DIRECTOR = new BigDecimal("1500.00");

  private static final BigDecimal[] RATES = {RATE_JUNIOR, RATE_MID, RATE_SENIOR, RATE_DIRECTOR};

  private static final String[] TASK_TITLES = {
    "Design brief",
    "Content creation",
    "Client review",
    "Strategy workshop",
    "Creative direction",
    "Revisions",
    "Final delivery",
    "Kick-off meeting",
    "Market research",
    "Competitor analysis"
  };

  public GenericDemoDataSeeder(
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
  }

  @Override
  protected void seedProfileData(String schemaName, UUID orgId) {
    Random rand = seededRandom(orgId);

    // 1. Members (4: 1 owner, 1 admin, 2 members)
    List<UUID> ownerIds = createMembers("owner", 1, 1);
    List<UUID> adminIds = createMembers("admin", 1, 2);
    List<UUID> memberIds = createMembers("member", 2, 3);

    List<UUID> allMemberIds = new ArrayList<>();
    allMemberIds.addAll(ownerIds);
    allMemberIds.addAll(adminIds);
    allMemberIds.addAll(memberIds);

    UUID primaryMemberId = ownerIds.getFirst();

    // 2. Customers (5, mix of lifecycle statuses)
    List<CustomerInfo> customers = createCustomers(primaryMemberId);

    // 3. Projects (8, linked to ACTIVE customers)
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

    log.info(
        "Generic demo data seeded: {} members, {} customers, {} projects, {} tasks, {} time entries",
        allMemberIds.size(),
        customers.size(),
        projects.size(),
        taskIds.size(),
        timeEntries.size());
  }

  private List<CustomerInfo> createCustomers(UUID createdBy) {
    record CustomerSpec(String name, String email, String phone, LifecycleStatus status) {}

    List<CustomerSpec> specs =
        List.of(
            new CustomerSpec(
                "Acme Holdings (Pty) Ltd",
                "accounts@acmeholdings.co.za",
                "+27 11 555 0001",
                LifecycleStatus.ACTIVE),
            new CustomerSpec(
                "Cape Digital Solutions",
                "info@capedigital.co.za",
                "+27 21 555 0002",
                LifecycleStatus.ACTIVE),
            new CustomerSpec(
                "Highveld Manufacturing",
                "procurement@highveldmfg.co.za",
                "+27 11 555 0003",
                LifecycleStatus.ACTIVE),
            new CustomerSpec(
                "Karoo Wine Estate",
                "admin@karoowines.co.za",
                "+27 23 555 0004",
                LifecycleStatus.ONBOARDING),
            new CustomerSpec(
                "Sandton Retail Group",
                "hello@sandtonretail.co.za",
                "+27 11 555 0005",
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

    List<ProjectSpec> specs =
        List.of(
            new ProjectSpec(
                "Website Redesign", "Full website redesign for improved UX and conversion", false),
            new ProjectSpec(
                "Q1 Strategy Review", "Quarterly strategic planning and performance review", false),
            new ProjectSpec(
                "Brand Identity Refresh", "Modernize brand assets and style guide", false),
            new ProjectSpec(
                "Social Media Campaign", "Multi-platform social media campaign for Q2", false),
            new ProjectSpec(
                "Annual Report Design", "Design and layout of 2025 annual report", true),
            new ProjectSpec(
                "Product Launch Strategy", "Go-to-market strategy for new product line", false),
            new ProjectSpec(
                "Customer Research Study",
                "In-depth customer behavior research and insights",
                true),
            new ProjectSpec("UX Audit", "Comprehensive UX audit and recommendations", false));

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
    int tasksPerProject = 50 / activeProjects.size(); // ~8 per active project
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
                "Demo task for " + project.name(),
                priority,
                null,
                null,
                createdBy);

        // Mix of statuses: ~40% OPEN, ~40% IN_PROGRESS, ~20% DONE
        int statusRoll = rand.nextInt(10);
        task = taskRepository.save(task);
        if (statusRoll < 4) {
          // Keep OPEN — assign via update
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
            new Task(
                project.id(), title, "Completed project task", "MEDIUM", null, null, createdBy);
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
    // DRAFT = just created, SENT = approved + sent, PAID = approved + sent + paid, VOID = approved
    // + sent + voided
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
      String invoiceNumber = "INV-2026-%03d".formatted(invoiceCount);
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
            invoice.recordPayment("PAY-DEMO-%03d".formatted(invoiceCount));
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
