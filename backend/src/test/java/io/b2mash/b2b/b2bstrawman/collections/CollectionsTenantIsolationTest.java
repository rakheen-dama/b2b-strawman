package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 590B.5 — MANDATORY tenant-isolation test (§8.4 row 11). Collection activities, gates and
 * collections settings written under tenant A's schema are invisible under tenant B's schema, and
 * vice versa. Isolation is the standard schema-per-tenant {@code search_path}; no Phase-83-specific
 * isolation code exists. All collections row types exist by this slice (591A's controllers add
 * nothing schema-level), so the whole domain is covered here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionsTenantIsolationTest {

  private static final String ORG_A = "org_collections_iso_a";
  private static final String ORG_B = "org_collections_iso_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CollectionsScanService scanService;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AiFirmProfileRepository firmProfileRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private Tenant tenantA;
  private Tenant tenantB;

  private record Tenant(String orgId, String schema, UUID memberId) {}

  @BeforeAll
  void setup() throws Exception {
    // A: full drafting stack (firm profile) with thresholds 7/21/45; B: no profile, 10/30/50.
    tenantA = provision(ORG_A, "Collections Iso A", "user_iso_a", 7, 21, 45, true);
    tenantB = provision(ORG_B, "Collections Iso B", "user_iso_b", 10, 30, 50, false);
  }

  private Tenant provision(
      String orgId,
      String orgName,
      String userSubject,
      int stage1,
      int stage2,
      int stage3,
      boolean withFirmProfile)
      throws Exception {
    provisioningService.provisionTenant(orgId, orgName, null);
    UUID member =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, orgId, userSubject, userSubject + "@test.com", "Owner", "owner"));
    String schema =
        orgSchemaMappingRepository.findByClerkOrgId(orgId).orElseThrow().getSchemaName();
    var tenant = new Tenant(orgId, schema, member);
    runIn(
        tenant,
        () -> {
          var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          settings.getCollections().updateCollectionsSettings(true, stage1, stage2, stage3, 90);
          orgSettingsRepository.save(settings);
          if (withFirmProfile && firmProfileRepository.findAll().isEmpty()) {
            firmProfileRepository.save(new AiFirmProfile(member));
          }
        });
    return tenant;
  }

  private void runIn(Tenant tenant, Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenant.schema())
        .where(RequestScopes.ORG_ID, tenant.orgId())
        .where(RequestScopes.MEMBER_ID, tenant.memberId())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  private <T> T inTenant(String schema, Supplier<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(() -> holder[0] = transactionTemplate.execute(tx -> body.get()));
    return holder[0];
  }

  /** Seeds a SENT overdue invoice in the tenant and runs its scan; returns the STAGE_1 activity. */
  private CollectionActivity seedAndScan(Tenant tenant, String name, int daysOverdue) {
    UUID[] invoiceId = new UUID[1];
    runIn(
        tenant,
        () -> {
          Customer customer =
              TestCustomerFactory.createActiveCustomer(
                  name, name.replace(' ', '_') + "@test.com", tenant.memberId());
          var savedCustomer = customerRepository.save(customer);
          var invoice =
              new Invoice(
                  savedCustomer.getId(),
                  "ZAR",
                  name,
                  name.replace(' ', '_') + "@test.com",
                  null,
                  "Test Org",
                  tenant.memberId());
          invoice.updateDraft(LocalDate.now().minusDays(daysOverdue), null, null, BigDecimal.ZERO);
          invoice.recalculateTotals(BigDecimal.valueOf(1500), false, BigDecimal.ZERO, false);
          invoice.approve("INV-" + UUID.randomUUID().toString().substring(0, 6), tenant.memberId());
          invoice.markSent();
          invoiceId[0] = invoiceRepository.save(invoice).getId();
        });
    runIn(tenant, scanService::scanForTenant);
    CollectionActivity[] holder = new CollectionActivity[1];
    runIn(
        tenant,
        () ->
            holder[0] =
                activityRepository
                    .findByInvoiceIdAndStage(invoiceId[0], CollectionStage.STAGE_1)
                    .orElseThrow());
    return holder[0];
  }

  @Test
  void activityAndGateWrittenInTenantA_areInvisibleUnderTenantB() {
    // A has the full drafting stack → real PROPOSED activity with a PENDING gate.
    var activityA = seedAndScan(tenantA, "Iso A Client", 12);
    assertThat(activityA.getStatus()).isEqualTo(CollectionActivityStatus.PROPOSED);
    UUID gateA = activityA.getGateId();
    assertThat(gateA).isNotNull();

    // Visible under A's schema.
    assertThat(
            inTenant(tenantA.schema(), () -> activityRepository.findOneById(activityA.getId()))
                .isPresent())
        .isTrue();
    assertThat(inTenant(tenantA.schema(), () -> gateRepository.findById(gateA)).isPresent())
        .isTrue();

    // Invisible under B's schema — pure search_path isolation.
    assertThat(
            inTenant(tenantB.schema(), () -> activityRepository.findOneById(activityA.getId()))
                .isPresent())
        .isFalse();
    assertThat(inTenant(tenantB.schema(), () -> gateRepository.findById(gateA)).isPresent())
        .isFalse();
    assertThat(
            inTenant(
                tenantB.schema(),
                () -> activityRepository.findByInvoiceId(activityA.getInvoiceId())))
        .isEmpty();
    assertThat(inTenant(tenantB.schema(), () -> activityRepository.findByGateId(gateA)).isPresent())
        .isFalse();
  }

  @Test
  void activityWrittenInTenantB_isInvisibleUnderTenantA() {
    // B has no firm profile → the scan still writes a (SKIPPED ai_unavailable) ledger row in B.
    var activityB = seedAndScan(tenantB, "Iso B Client", 15);
    assertThat(activityB.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
    assertThat(activityB.getReason()).isEqualTo("ai_unavailable");

    assertThat(
            inTenant(tenantB.schema(), () -> activityRepository.findOneById(activityB.getId()))
                .isPresent())
        .isTrue();

    assertThat(
            inTenant(tenantA.schema(), () -> activityRepository.findOneById(activityB.getId()))
                .isPresent())
        .isFalse();
    assertThat(
            inTenant(
                tenantA.schema(),
                () -> activityRepository.findByInvoiceId(activityB.getInvoiceId())))
        .isEmpty();
  }

  @Test
  void collectionsSettings_areIndependentPerTenantSchema() {
    // Seeded differently in setup: A = 7/21/45, B = 10/30/50.
    var settingsA =
        inTenant(
            tenantA.schema(),
            () -> orgSettingsRepository.findForCurrentTenant().orElseThrow().getCollections());
    var settingsB =
        inTenant(
            tenantB.schema(),
            () -> orgSettingsRepository.findForCurrentTenant().orElseThrow().getCollections());
    assertThat(settingsA.getStage1DaysOverdue()).isEqualTo(7);
    assertThat(settingsB.getStage1DaysOverdue()).isEqualTo(10);

    // Mutating B must not bleed into A. Restored afterwards so sibling tests (which scan B) are
    // order-independent.
    try {
      runIn(
          tenantB,
          () -> {
            var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
            settings.getCollections().updateCollectionsSettings(false, 11, 31, 51, 91);
            orgSettingsRepository.save(settings);
          });

      var settingsAAfter =
          inTenant(
              tenantA.schema(),
              () -> orgSettingsRepository.findForCurrentTenant().orElseThrow().getCollections());
      var settingsBAfter =
          inTenant(
              tenantB.schema(),
              () -> orgSettingsRepository.findForCurrentTenant().orElseThrow().getCollections());
      assertThat(settingsAAfter.isCollectionsEnabled()).isTrue();
      assertThat(settingsAAfter.getStage1DaysOverdue()).isEqualTo(7);
      assertThat(settingsBAfter.isCollectionsEnabled()).isFalse();
      assertThat(settingsBAfter.getStage1DaysOverdue()).isEqualTo(11);
    } finally {
      runIn(
          tenantB,
          () -> {
            var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
            settings.getCollections().updateCollectionsSettings(true, 10, 30, 50, 90);
            orgSettingsRepository.save(settings);
          });
    }
  }
}
