package io.b2mash.b2b.b2bstrawman.demo.seed;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.util.List;
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
class LegalDemoDataSeederTest {

  private final LegalDemoDataSeeder legalDemoDataSeeder;
  private final TenantProvisioningService tenantProvisioningService;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final OrgSchemaMappingRepository mappingRepository;
  private final OrganizationRepository organizationRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final TimeEntryRepository timeEntryRepository;

  private String schemaName;
  private UUID orgId;

  @Autowired
  LegalDemoDataSeederTest(
      LegalDemoDataSeeder legalDemoDataSeeder,
      TenantProvisioningService tenantProvisioningService,
      TenantTransactionHelper tenantTransactionHelper,
      OrgSchemaMappingRepository mappingRepository,
      OrganizationRepository organizationRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      TimeEntryRepository timeEntryRepository) {
    this.legalDemoDataSeeder = legalDemoDataSeeder;
    this.tenantProvisioningService = tenantProvisioningService;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.mappingRepository = mappingRepository;
    this.organizationRepository = organizationRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.timeEntryRepository = timeEntryRepository;
  }

  @BeforeAll
  void setUp() {
    // Provision a tenant schema for the test
    String slug = "legal-seeder-test-" + UUID.randomUUID().toString().substring(0, 8);
    tenantProvisioningService.provisionTenant(slug, "Legal Seeder Test Org", "legal");

    schemaName = mappingRepository.findByExternalOrgId(slug).orElseThrow().getSchemaName();
    orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();

    // Run the seeder
    legalDemoDataSeeder.seed(schemaName, orgId);
  }

  @Test
  void seeder_createsCorrectCustomerNames() {
    var names = new AtomicReference<List<String>>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t ->
            names.set(
                customerRepository.findAll().stream().map(c -> c.getName()).sorted().toList()));

    List<String> expected =
        List.of(
            "Botha Family Estate",
            "Cele Holdings",
            "Dlamini Property Trust",
            "Msimang Transport (Pty) Ltd",
            "Naidoo & Partners Developers");

    assertEquals(expected, names.get(), "Customer names must match law firm clients");
  }

  @Test
  void seeder_createsLegalProjects() {
    var projectNames = new AtomicReference<List<String>>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t ->
            projectNames.set(
                projectRepository.findAll().stream().map(p -> p.getName()).sorted().toList()));

    List<String> names = projectNames.get();
    assertTrue(
        names.stream().anyMatch(n -> n.contains("Property Transfer")),
        "Should have Property Transfer project");
    assertTrue(
        names.stream().anyMatch(n -> n.contains("Lease Review")),
        "Should have Commercial Lease Review project");
    assertTrue(
        names.stream().anyMatch(n -> n.contains("Estate Administration")),
        "Should have Estate Administration project");
    assertTrue(
        names.stream().anyMatch(n -> n.contains("Labour Dispute")),
        "Should have Labour Dispute project");
  }

  @Test
  void seeder_ratesInLegalRange() {
    var allInRange = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          BigDecimal minRate = new BigDecimal("1200.00");
          BigDecimal maxRate = new BigDecimal("3500.00");
          var timeEntries = timeEntryRepository.findAll();
          boolean inRange =
              timeEntries.stream()
                  .filter(entry -> entry.getBillingRateSnapshot() != null)
                  .allMatch(
                      entry ->
                          entry.getBillingRateSnapshot().compareTo(minRate) >= 0
                              && entry.getBillingRateSnapshot().compareTo(maxRate) <= 0);
          allInRange.set(inRange);
        });
    assertTrue(
        allInRange.get(), "All billing rate snapshots must be within R1,200-R3,500 legal range");
  }

  @Test
  void seeder_gracefullySkipsAbsentLegalModules() {
    // Provision a fresh tenant to verify seeding completes without error
    // even when court calendar entity infrastructure is not available
    String slug = "legal-skip-test-" + UUID.randomUUID().toString().substring(0, 8);
    tenantProvisioningService.provisionTenant(slug, "Legal Skip Test Org", "legal");

    String freshSchema = mappingRepository.findByExternalOrgId(slug).orElseThrow().getSchemaName();
    UUID freshOrgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();

    assertDoesNotThrow(
        () -> legalDemoDataSeeder.seed(freshSchema, freshOrgId),
        "Seeder must complete without error when legal modules are absent");
  }
}
