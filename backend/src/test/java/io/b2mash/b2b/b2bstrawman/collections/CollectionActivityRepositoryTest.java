package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.CollectionsSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Repository-level tests for the Phase-83 collections foundation: V133 provisioning clean, the
 * {@code CollectionActivity} round-trip and mutators, the {@code
 * ux_collection_activity_invoice_stage} UNIQUE constraint, the {@code CollectionsSettings}
 * defaults, and {@code Customer.collectionsExempt} persistence. Embedded Postgres, {@code
 * provisionTenant} in {@code @BeforeAll}, ScopedValue tenant binding — no Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionActivityRepositoryTest {

  private static final String ORG_ID = "org_collections_repo_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CollectionActivityRepository collectionActivityRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private CustomerRepository customerRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    // Provisioning runs all tenant migrations including V133 — if V133 is broken this throws.
    provisioningService.provisionTenant(ORG_ID, "Collections Repo Test Org", null);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_coll_repo", "coll_repo@test.com", "Coll Repo", "owner");
    memberId = UUID.fromString(memberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  @Test
  void persistAndFindOneByIdRoundTrip() {
    UUID invoiceId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID gateId = UUID.randomUUID();
    UUID emailLogId = UUID.randomUUID();
    UUID[] idHolder = new UUID[1];

    runInTenant(
        () -> {
          var activity =
              new CollectionActivity(
                  invoiceId,
                  customerId,
                  CollectionStage.STAGE_1,
                  CollectionActivityStatus.PROPOSED,
                  8,
                  "no_recipient");
          activity.markProposed(gateId, 9);
          activity.markSent(emailLogId);
          idHolder[0] = collectionActivityRepository.saveAndFlush(activity).getId();
        });

    runInTenant(
        () -> {
          var reloaded = collectionActivityRepository.findOneById(idHolder[0]).orElseThrow();
          assertThat(reloaded.getInvoiceId()).isEqualTo(invoiceId);
          assertThat(reloaded.getCustomerId()).isEqualTo(customerId);
          assertThat(reloaded.getStage()).isEqualTo(CollectionStage.STAGE_1);
          assertThat(reloaded.getStatus()).isEqualTo(CollectionActivityStatus.SENT);
          assertThat(reloaded.getGateId()).isEqualTo(gateId);
          assertThat(reloaded.getEmailDeliveryLogId()).isEqualTo(emailLogId);
          // markProposed snapshotted daysOverdue=9 and cleared the ctor reason.
          assertThat(reloaded.getDaysOverdueAtAction()).isEqualTo(9);
          assertThat(reloaded.getReason()).isNull();
          assertThat(reloaded.getCreatedAt()).isNotNull();
          assertThat(reloaded.getUpdatedAt()).isNotNull();
          assertThat(reloaded.getVersion()).isEqualTo(0);
        });
  }

  @Test
  void uniqueInvoiceStageIndexRejectsDuplicate() {
    UUID invoiceId = UUID.randomUUID();
    runInTenant(
        () -> {
          var first =
              new CollectionActivity(
                  invoiceId,
                  UUID.randomUUID(),
                  CollectionStage.STAGE_2,
                  CollectionActivityStatus.PROPOSED,
                  21,
                  null);
          collectionActivityRepository.saveAndFlush(first);
        });

    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> {
                      var duplicate =
                          new CollectionActivity(
                              invoiceId,
                              UUID.randomUUID(),
                              CollectionStage.STAGE_2,
                              CollectionActivityStatus.PROPOSED,
                              22,
                              null);
                      // flush forces INSERT → unique (invoice_id, stage) index fires
                      collectionActivityRepository.saveAndFlush(duplicate);
                    }))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findByInvoiceIdAndStatusAndPagedFindByCustomerId() {
    UUID invoiceId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID otherCustomerId = UUID.randomUUID();
    UUID[] proposedId = new UUID[1];
    UUID[] sentId = new UUID[1];
    UUID[] otherCustId = new UUID[1];

    runInTenant(
        () -> {
          var proposed =
              new CollectionActivity(
                  invoiceId,
                  customerId,
                  CollectionStage.STAGE_1,
                  CollectionActivityStatus.PROPOSED,
                  7,
                  null);
          proposedId[0] = collectionActivityRepository.saveAndFlush(proposed).getId();

          var sent =
              new CollectionActivity(
                  invoiceId,
                  customerId,
                  CollectionStage.STAGE_2,
                  CollectionActivityStatus.SENT,
                  21,
                  null);
          sentId[0] = collectionActivityRepository.saveAndFlush(sent).getId();

          var otherCust =
              new CollectionActivity(
                  UUID.randomUUID(),
                  otherCustomerId,
                  CollectionStage.STAGE_1,
                  CollectionActivityStatus.PROPOSED,
                  7,
                  null);
          otherCustId[0] = collectionActivityRepository.saveAndFlush(otherCust).getId();
        });

    runInTenant(
        () -> {
          var proposedForInvoice =
              collectionActivityRepository.findByInvoiceIdAndStatus(
                  invoiceId, CollectionActivityStatus.PROPOSED);
          assertThat(proposedForInvoice)
              .extracting(CollectionActivity::getId)
              .contains(proposedId[0]);
          assertThat(proposedForInvoice)
              .extracting(CollectionActivity::getId)
              .doesNotContain(sentId[0], otherCustId[0]);

          var byInvoice = collectionActivityRepository.findByInvoiceId(invoiceId);
          assertThat(byInvoice)
              .extracting(CollectionActivity::getId)
              .contains(proposedId[0], sentId[0])
              .doesNotContain(otherCustId[0]);

          var byStage =
              collectionActivityRepository.findByInvoiceIdAndStage(
                  invoiceId, CollectionStage.STAGE_1);
          assertThat(byStage).isPresent();
          assertThat(byStage.get().getId()).isEqualTo(proposedId[0]);

          var page =
              collectionActivityRepository.findByCustomerId(customerId, PageRequest.of(0, 10));
          // stage2 (sent) was created after stage1 (proposed) → newest-first ordering.
          assertThat(page.getContent())
              .extracting(CollectionActivity::getId)
              .containsExactly(sentId[0], proposedId[0])
              .doesNotContain(otherCustId[0]);
        });
  }

  @Test
  void collectionsSettingsDefaultsAndGetterNeverNull() {
    // Fresh entity: field initialisers give enabled=false and 7/21/45/60 defaults, non-null getter.
    var fresh = new OrgSettings("ZAR");
    CollectionsSettings freshGroup = fresh.getCollections();
    assertThat(freshGroup).isNotNull();
    assertThat(freshGroup.isCollectionsEnabled()).isFalse();
    assertThat(freshGroup.getStage1DaysOverdue()).isEqualTo(7);
    assertThat(freshGroup.getStage2DaysOverdue()).isEqualTo(21);
    assertThat(freshGroup.getStage3DaysOverdue()).isEqualTo(45);
    assertThat(freshGroup.getEscalateDaysOverdue()).isEqualTo(60);

    // Loaded row: the provisioned tenant's org_settings reloads a non-null group with the NOT NULL
    // boolean present.
    runInTenant(
        () -> {
          var loaded = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          assertThat(loaded.getCollections()).isNotNull();
          assertThat(loaded.getCollections().isCollectionsEnabled()).isFalse();
        });
  }

  @Test
  void customerCollectionsExemptPersistsAndDefaultsFalse() {
    UUID[] idHolder = new UUID[1];
    runInTenant(
        () -> {
          Customer customer =
              TestCustomerFactory.createActiveCustomer("Debtor Co", "debtor@test.com", memberId);
          idHolder[0] = customerRepository.saveAndFlush(customer).getId();
        });

    runInTenant(
        () -> {
          var reloaded = customerRepository.findById(idHolder[0]).orElseThrow();
          assertThat(reloaded.isCollectionsExempt()).isFalse();
          reloaded.setCollectionsExempt(true);
          customerRepository.saveAndFlush(reloaded);
        });

    runInTenant(
        () -> {
          var reloaded = customerRepository.findById(idHolder[0]).orElseThrow();
          assertThat(reloaded.isCollectionsExempt()).isTrue();
        });
  }
}
