package io.b2mash.b2b.b2bstrawman.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.billing.BillingMethod;
import io.b2mash.b2b.b2bstrawman.billing.Subscription;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.demo.seed.GenericDemoDataSeeder;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoReseedTest {

  private final DemoProvisionService demoProvisionService;
  private final GenericDemoDataSeeder genericDemoDataSeeder;
  private final TenantProvisioningService tenantProvisioningService;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final OrgSchemaMappingRepository mappingRepository;
  private final OrganizationRepository organizationRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final MemberRepository memberRepository;
  private final OrgRoleRepository orgRoleRepository;
  private final TransactionTemplate txTemplate;

  private String schemaName;
  private UUID orgId;

  @Autowired
  DemoReseedTest(
      DemoProvisionService demoProvisionService,
      GenericDemoDataSeeder genericDemoDataSeeder,
      TenantProvisioningService tenantProvisioningService,
      TenantTransactionHelper tenantTransactionHelper,
      OrgSchemaMappingRepository mappingRepository,
      OrganizationRepository organizationRepository,
      SubscriptionRepository subscriptionRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      TimeEntryRepository timeEntryRepository,
      MemberRepository memberRepository,
      OrgRoleRepository orgRoleRepository,
      TransactionTemplate txTemplate) {
    this.demoProvisionService = demoProvisionService;
    this.genericDemoDataSeeder = genericDemoDataSeeder;
    this.tenantProvisioningService = tenantProvisioningService;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.mappingRepository = mappingRepository;
    this.organizationRepository = organizationRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.memberRepository = memberRepository;
    this.orgRoleRepository = orgRoleRepository;
    this.txTemplate = txTemplate;
  }

  @BeforeAll
  void setUp() {
    String slug = "reseed-test-" + UUID.randomUUID().toString().substring(0, 8);
    tenantProvisioningService.provisionTenant(slug, "Reseed Test Org", "generic");
    schemaName = mappingRepository.findByExternalOrgId(slug).orElseThrow().getSchemaName();
    orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();

    // Seed initial demo data
    genericDemoDataSeeder.seed(schemaName, orgId);

    // Override subscription to ACTIVE/PILOT so reseed is allowed
    txTemplate.executeWithoutResult(
        tx -> {
          Subscription sub = subscriptionRepository.findByOrganizationId(orgId).orElseThrow();
          sub.adminTransitionTo(Subscription.SubscriptionStatus.ACTIVE);
          sub.setBillingMethod(BillingMethod.PILOT);
          sub.setAdminNote("Demo tenant provisioned by admin test-admin. Profile: generic");
          subscriptionRepository.save(sub);
        });
  }

  @AfterEach
  void resetBillingMethod() {
    // Reset billing method back to PILOT after each test (in case tests 4/5 changed it)
    txTemplate.executeWithoutResult(
        tx -> {
          Subscription sub = subscriptionRepository.findByOrganizationId(orgId).orElseThrow();
          if (sub.getBillingMethod() != BillingMethod.PILOT) {
            sub.setBillingMethod(BillingMethod.PILOT);
            subscriptionRepository.save(sub);
          }
        });
  }

  @Test
  void reseed_clearsAllTransactionalData() {
    // Verify data exists before reseed
    var customerCountBefore = new AtomicReference<Long>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName, orgId.toString(), t -> customerCountBefore.set(customerRepository.count()));
    assertTrue(customerCountBefore.get() > 0, "Should have customers before reseed");

    // Execute reseed
    var response = demoProvisionService.reseed(orgId, "test-admin");

    assertTrue(response.success(), "Reseed should succeed");
    assertEquals(orgId, response.organizationId());
    assertEquals("generic", response.verticalProfile());

    // Verify data exists after reseed (re-seeded)
    var customerCountAfter = new AtomicReference<Long>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName, orgId.toString(), t -> customerCountAfter.set(customerRepository.count()));
    assertTrue(customerCountAfter.get() > 0, "Should have customers after reseed");
  }

  @Test
  void reseed_preservesConfigurationData() {
    // Count config data before reseed — org_roles is a config table that is NOT truncated
    var orgRoleCountBefore = new AtomicReference<Long>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName, orgId.toString(), t -> orgRoleCountBefore.set(orgRoleRepository.count()));
    assertTrue(orgRoleCountBefore.get() > 0, "Should have org roles before reseed");

    // Execute reseed
    var response = demoProvisionService.reseed(orgId, "test-admin");
    assertTrue(response.success(), "Reseed should succeed");

    // Verify config table (org_roles) is preserved after reseed
    var orgRoleCountAfter = new AtomicReference<Long>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName, orgId.toString(), t -> orgRoleCountAfter.set(orgRoleRepository.count()));
    assertEquals(
        orgRoleCountBefore.get(),
        orgRoleCountAfter.get(),
        "Org role count should be preserved after reseed");
  }

  @Test
  void reseed_repopulatesTransactionalData() {
    // Execute reseed
    var response = demoProvisionService.reseed(orgId, "test-admin");
    assertTrue(response.success(), "Reseed should succeed");

    // Verify data volumes match fresh seed expectations
    var customerCount = new AtomicReference<Long>();
    var projectCount = new AtomicReference<Long>();
    var timeEntryCount = new AtomicReference<Long>();
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          customerCount.set(customerRepository.count());
          projectCount.set(projectRepository.count());
          timeEntryCount.set(timeEntryRepository.count());
        });

    assertEquals(5L, customerCount.get(), "Should have 5 customers after reseed");
    assertTrue(
        projectCount.get() >= 8,
        "Should have >= 8 projects after reseed, found: " + projectCount.get());
    assertTrue(
        timeEntryCount.get() >= 100,
        "Should have >= 100 time entries after reseed, found: " + timeEntryCount.get());
  }

  @Test
  void reseed_rejectedForPayfastBillingMethod() {
    // Change billing method to PAYFAST
    txTemplate.executeWithoutResult(
        tx -> {
          Subscription sub = subscriptionRepository.findByOrganizationId(orgId).orElseThrow();
          sub.setBillingMethod(BillingMethod.PAYFAST);
          subscriptionRepository.save(sub);
        });

    // Reseed should be rejected
    assertThrows(ForbiddenException.class, () -> demoProvisionService.reseed(orgId, "test-admin"));
  }

  @Test
  void reseed_rejectedForManualBillingMethod() {
    // Change billing method to MANUAL
    txTemplate.executeWithoutResult(
        tx -> {
          Subscription sub = subscriptionRepository.findByOrganizationId(orgId).orElseThrow();
          sub.setBillingMethod(BillingMethod.MANUAL);
          subscriptionRepository.save(sub);
        });

    // Reseed should be rejected
    assertThrows(ForbiddenException.class, () -> demoProvisionService.reseed(orgId, "test-admin"));
  }
}
