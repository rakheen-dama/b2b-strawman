package io.b2mash.b2b.b2bstrawman.demo.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingDemoDataSeederTest {

  private final AccountingDemoDataSeeder accountingDemoDataSeeder;
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
  AccountingDemoDataSeederTest(
      AccountingDemoDataSeeder accountingDemoDataSeeder,
      TenantProvisioningService tenantProvisioningService,
      TenantTransactionHelper tenantTransactionHelper,
      OrgSchemaMappingRepository mappingRepository,
      OrganizationRepository organizationRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      TimeEntryRepository timeEntryRepository) {
    this.accountingDemoDataSeeder = accountingDemoDataSeeder;
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
    String slug = "acct-seeder-test-" + UUID.randomUUID().toString().substring(0, 8);
    tenantProvisioningService.provisionTenant(slug, "Accounting Seeder Test Org", "accounting");

    schemaName = mappingRepository.findByExternalOrgId(slug).orElseThrow().getSchemaName();
    orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();

    // Run the seeder
    accountingDemoDataSeeder.seed(schemaName, orgId);
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
            "Berg & Berg Attorneys",
            "Disa Financial Services",
            "Karoo Investments",
            "Protea Trading (Pty) Ltd",
            "Van der Merwe & Associates");

    assertEquals(expected, names.get(), "Customer names must match accounting firm clients");
  }

  @Test
  void seeder_createsAccountingProjects() {
    var projectNames = new AtomicReference<List<String>>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t ->
            projectNames.set(
                projectRepository.findAll().stream().map(p -> p.getName()).sorted().toList()));

    List<String> names = projectNames.get();
    assertTrue(
        names.stream().anyMatch(n -> n.contains("Annual Financials")),
        "Should have Annual Financials project");
    assertTrue(
        names.stream().anyMatch(n -> n.contains("VAT Registration")),
        "Should have VAT Registration project");
    assertTrue(
        names.stream().anyMatch(n -> n.contains("BBBEE")), "Should have BBBEE Audit project");
    assertTrue(
        names.stream().anyMatch(n -> n.contains("Bookkeeping")),
        "Should have Monthly Bookkeeping project");
    assertTrue(names.stream().anyMatch(n -> n.contains("ITR14")), "Should have SARS ITR14 project");
  }

  @Test
  void seeder_ratesInAccountingRange() {
    var allInRange = new AtomicReference<Boolean>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          BigDecimal minRate = new BigDecimal("650.00");
          BigDecimal maxRate = new BigDecimal("1200.00");
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
        allInRange.get(), "All billing rate snapshots must be within R650-R1,200 accounting range");
  }
}
