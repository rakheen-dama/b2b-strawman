package io.b2mash.b2b.b2bstrawman.demo.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenericDemoDataSeederTest {

  private final GenericDemoDataSeeder genericDemoDataSeeder;
  private final TenantProvisioningService tenantProvisioningService;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final OrgSchemaMappingRepository mappingRepository;
  private final OrganizationRepository organizationRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;

  private String schemaName;
  private UUID orgId;

  @Autowired
  GenericDemoDataSeederTest(
      GenericDemoDataSeeder genericDemoDataSeeder,
      TenantProvisioningService tenantProvisioningService,
      TenantTransactionHelper tenantTransactionHelper,
      OrgSchemaMappingRepository mappingRepository,
      OrganizationRepository organizationRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository) {
    this.genericDemoDataSeeder = genericDemoDataSeeder;
    this.tenantProvisioningService = tenantProvisioningService;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.mappingRepository = mappingRepository;
    this.organizationRepository = organizationRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
  }

  @BeforeAll
  void setUp() {
    // Provision a tenant schema for the test
    String slug = "demo-seeder-test-" + UUID.randomUUID().toString().substring(0, 8);
    var result = tenantProvisioningService.provisionTenant(slug, "Demo Seeder Test Org", "generic");

    schemaName = mappingRepository.findByExternalOrgId(slug).orElseThrow().getSchemaName();

    // Get the org ID from the actual Organization record (not derived from slug)
    orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();

    // Run the seeder
    genericDemoDataSeeder.seed(schemaName, orgId);
  }

  @Test
  void seeder_createsCorrectNumberOfCustomers() {
    var count = new AtomicReference<Long>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName, orgId.toString(), t -> count.set(customerRepository.count()));
    assertEquals(5L, count.get());
  }

  @Test
  void seeder_createsCorrectNumberOfProjects() {
    var count = new AtomicReference<Long>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName, orgId.toString(), t -> count.set(projectRepository.count()));
    long projectCount = count.get();
    assertTrue(
        projectCount >= 8 && projectCount <= 12,
        "Expected 8-12 projects but found " + projectCount);
  }

  @Test
  void allTimeEntries_referenceValidTasks() {
    var allValid = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          var taskIds = taskRepository.findAll().stream().map(task -> task.getId()).toList();
          var timeEntries = timeEntryRepository.findAll();
          boolean valid =
              timeEntries.stream().allMatch(entry -> taskIds.contains(entry.getTaskId()));
          allValid.set(valid);
        });
    assertTrue(allValid.get(), "All time entries must reference valid tasks");
  }

  @Test
  void relativeDate_withinExpectedRange() {
    var allInRange = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          LocalDate earliest = LocalDate.now().minusDays(91);
          LocalDate latest = LocalDate.now();
          var timeEntries = timeEntryRepository.findAll();
          boolean inRange =
              timeEntries.stream()
                  .allMatch(
                      entry ->
                          !entry.getDate().isBefore(earliest) && !entry.getDate().isAfter(latest));
          allInRange.set(inRange);
        });
    assertTrue(allInRange.get(), "All time entry dates must be within the last 91 days");
  }

  @Test
  void deterministicSeed_producesReproducibleData() {
    // Provision a second tenant with a known orgId
    UUID fixedOrgId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    String slug1 = "deterministic-test-1-" + UUID.randomUUID().toString().substring(0, 6);
    String slug2 = "deterministic-test-2-" + UUID.randomUUID().toString().substring(0, 6);

    tenantProvisioningService.provisionTenant(slug1, "Deterministic Test 1", "generic");
    tenantProvisioningService.provisionTenant(slug2, "Deterministic Test 2", "generic");

    String schema1 = mappingRepository.findByExternalOrgId(slug1).orElseThrow().getSchemaName();
    String schema2 = mappingRepository.findByExternalOrgId(slug2).orElseThrow().getSchemaName();

    genericDemoDataSeeder.seed(schema1, fixedOrgId);
    genericDemoDataSeeder.seed(schema2, fixedOrgId);

    // Compare customer names — should be identical since same orgId seeds the same Random
    var names1 = new AtomicReference<java.util.List<String>>();
    var names2 = new AtomicReference<java.util.List<String>>();

    tenantTransactionHelper.executeInTenantTransaction(
        schema1,
        fixedOrgId.toString(),
        t ->
            names1.set(
                customerRepository.findAll().stream().map(c -> c.getName()).sorted().toList()));

    tenantTransactionHelper.executeInTenantTransaction(
        schema2,
        fixedOrgId.toString(),
        t ->
            names2.set(
                customerRepository.findAll().stream().map(c -> c.getName()).sorted().toList()));

    assertEquals(names1.get(), names2.get(), "Same orgId should produce identical customer names");
  }
}
