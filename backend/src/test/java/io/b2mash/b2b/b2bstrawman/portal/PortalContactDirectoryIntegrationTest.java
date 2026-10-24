package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.datarequest.DataAnonymizationService;
import io.b2mash.b2b.b2bstrawman.datarequest.DataSubjectRequest;
import io.b2mash.b2b.b2bstrawman.datarequest.DataSubjectRequestRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.directory.PortalContactDirectoryEntry;
import io.b2mash.b2b.b2bstrawman.portal.directory.PortalContactDirectoryRepository;
import io.b2mash.b2b.b2bstrawman.portal.directory.PortalContactDirectoryService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 88 U11 (ADR-351): the global {@code portal.portal_contact_directory} stays in step with
 * each tenant's ACTIVE portal contacts — create (incl. auto-provisioning), suspend, archive,
 * anonymization ("email change", both the standalone and the DSAR path), delete, rollback — and is
 * rebuilt by {@code /internal/portal/resync/{orgId}} (the backfill runner is covered in {@code
 * portal.directory.PortalContactDirectoryBackfillIntegrationTest}). Directory and marker tables
 * accumulate rows from every test class, so every assertion is scoped to this class's orgs / tenant
 * schemas / unique emails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalContactDirectoryIntegrationTest {

  private static final String ORG_A = "org_contact_directory_a";
  private static final String ORG_B = "org_contact_directory_b";
  private static final String ORG_NAME_A = "Directory Test Firm A";
  private static final String ORG_NAME_B = "Directory Test Firm B";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private PortalContactDirectoryRepository directoryRepository;
  @Autowired private PortalContactDirectoryService directoryService;
  @Autowired private DataAnonymizationService dataAnonymizationService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DataSubjectRequestRepository dataSubjectRequestRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private String schemaA;
  private String schemaB;
  private UUID memberIdA;
  private UUID customerA1;
  private UUID customerA2;
  private UUID customerB1;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_A, ORG_NAME_A, null);
    provisioningService.provisionTenant(ORG_B, ORG_NAME_B, null);
    memberIdA =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_A,
                "user_dir_owner_a",
                "dir_owner_a@test.com",
                "Directory Owner A",
                "owner"));
    UUID memberIdB =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_B,
                "user_dir_owner_b",
                "dir_owner_b@test.com",
                "Directory Owner B",
                "owner"));
    schemaA = orgSchemaMappingRepository.findByClerkOrgId(ORG_A).orElseThrow().getSchemaName();
    schemaB = orgSchemaMappingRepository.findByClerkOrgId(ORG_B).orElseThrow().getSchemaName();

    customerA1 =
        inA(
            () ->
                customerService
                    .createCustomer(
                        "Dir Customer A1", "dir-cust-a1@test.com", null, null, null, memberIdA)
                    .getId());
    customerA2 =
        inA(
            () ->
                customerService
                    .createCustomer(
                        "Dir Customer A2", "dir-cust-a2@test.com", null, null, null, memberIdA)
                    .getId());
    customerB1 =
        inB(
            () ->
                customerService
                    .createCustomer(
                        "Dir Customer B1", "dir-cust-b1@test.com", null, null, null, memberIdB)
                    .getId());
  }

  // ── 1. Auto-provisioned contact (customer created via the API) ─────────────

  @Test
  void autoProvisionedContactIsRoutedUnderNormalisedEmail() throws Exception {
    TestEntityHelper.createCustomer(
        mockMvc,
        TestJwtFactory.ownerJwt(ORG_A, "user_dir_owner_a"),
        "Dir Auto Client",
        "Dir.Auto.Client@Test.com");

    var entry = directoryRepository.find("dir.auto.client@test.com", ORG_A);
    assertThat(entry).isPresent();
    assertThat(entry.get().tenantSchema()).isEqualTo(schemaA);
    assertThat(entry.get().orgName()).isEqualTo(ORG_NAME_A);
    assertThat(entry.get().updatedAt()).isNotNull();
  }

  // ── 2. createContact ──────────────────────────────────────────────────────

  @Test
  void createContactUpsertsDirectoryRow() {
    createContactInA(customerA1, "Create.Me@Test.com");

    var entry = directoryRepository.find("create.me@test.com", ORG_A);
    assertThat(entry).isPresent();
    assertThat(entry.get().tenantSchema()).isEqualTo(schemaA);
    assertThat(entry.get().orgName()).isEqualTo(ORG_NAME_A);
  }

  // ── 3. archiveContact ─────────────────────────────────────────────────────

  @Test
  void archivingTheOnlyActiveContactRemovesTheRow() {
    var contact = createContactInA(customerA1, "archive-only@test.com");
    assertThat(directoryRepository.find("archive-only@test.com", ORG_A)).isPresent();

    inA(() -> portalContactService.archiveContact(contact.getId()));

    assertThat(directoryRepository.find("archive-only@test.com", ORG_A)).isEmpty();
  }

  // ── 4. suspendContact (D-3) ───────────────────────────────────────────────

  @Test
  void suspendingTheOnlyActiveContactRemovesTheRow() {
    var contact = createContactInA(customerA1, "suspend-only@test.com");
    assertThat(directoryRepository.find("suspend-only@test.com", ORG_A)).isPresent();

    inA(() -> portalContactService.suspendContact(contact.getId()));

    assertThat(directoryRepository.find("suspend-only@test.com", ORG_A)).isEmpty();
  }

  // ── 5. Presence semantics: same email on two customers ───────────────────

  @Test
  void rowIsKeptWhileAnotherActiveContactSharesTheEmail() {
    var first = createContactInA(customerA1, "shared-email@test.com");
    var second = createContactInA(customerA2, "Shared-Email@Test.com");

    inA(() -> portalContactService.archiveContact(first.getId()));
    assertThat(directoryRepository.find("shared-email@test.com", ORG_A)).isPresent();

    inA(() -> portalContactService.archiveContact(second.getId()));
    assertThat(directoryRepository.find("shared-email@test.com", ORG_A)).isEmpty();
  }

  // ── 6. Email change via anonymization (D-4a) ─────────────────────────────

  @Test
  void anonymizationRemovesTheRealEmailAndNeverRoutesTheAnonymisedOne() {
    UUID customerId =
        inA(
            () ->
                customerService
                    .createCustomer(
                        "Dir Anon Customer", "dir-anon@test.com", null, null, null, memberIdA)
                    .getId());
    // Auto-provisioned contact carries the customer's email.
    assertThat(directoryRepository.find("dir-anon@test.com", ORG_A)).isPresent();

    inA(
        () ->
            dataAnonymizationService.anonymizeCustomer(
                customerId, "Dir Anon Customer", "POPIA deletion", memberIdA));

    assertThat(directoryRepository.find("dir-anon@test.com", ORG_A)).isEmpty();
    List<PortalContact> contacts = inA(() -> portalContactRepository.findByCustomerId(customerId));
    assertThat(contacts).isNotEmpty();
    for (var contact : contacts) {
      assertThat(contact.getEmail()).endsWith("@anonymized.invalid");
      assertThat(
              directoryRepository.find(
                  PortalContactDirectoryService.normalise(contact.getEmail()), ORG_A))
          .isEmpty();
    }
  }

  // ── 6b. DSAR deletion (executeAnonymization) also removes the real email (D-4a) ──

  @Test
  void dsarDeletionRemovesTheRealEmailFromTheDirectory() {
    UUID customerId =
        inA(
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Dir Dsar Customer", "dir-dsar-customer@test.com", memberIdA))
                    .getId());
    var contact = createContactInA(customerId, "Dir.Dsar@Test.com");
    assertThat(directoryRepository.find("dir.dsar@test.com", ORG_A)).isPresent();
    UUID requestId =
        inA(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var request =
                          new DataSubjectRequest(
                              customerId,
                              "DELETION",
                              "Delete all data",
                              memberIdA,
                              LocalDate.now().plusDays(30));
                      request = dataSubjectRequestRepository.save(request);
                      request.startProcessing(memberIdA);
                      return dataSubjectRequestRepository.save(request).getId();
                    }));

    inA(
        () ->
            dataAnonymizationService.executeAnonymization(
                requestId, "Dir Dsar Customer", memberIdA));

    assertThat(directoryRepository.find("dir.dsar@test.com", ORG_A)).isEmpty();
    var anonymised = inA(() -> portalContactRepository.findById(contact.getId())).orElseThrow();
    assertThat(anonymised.getEmail()).endsWith("@anonymized.invalid");
    assertThat(
            directoryRepository.find(
                PortalContactDirectoryService.normalise(anonymised.getEmail()), ORG_A))
        .isEmpty();
  }

  // ── 7. Delete: reconcile with no remaining ACTIVE contact deletes the row (D-2) ──

  @Test
  void reconcileAfterContactDeleteRemovesTheRow() {
    var contact = createContactInA(customerA1, "delete-me@test.com");
    assertThat(directoryRepository.find("delete-me@test.com", ORG_A)).isPresent();

    inA(
        () -> {
          transactionTemplate.executeWithoutResult(
              tx -> portalContactRepository.deleteById(contact.getId()));
          directoryService.reconcile("delete-me@test.com", ORG_A, schemaA, ORG_NAME_A);
          return null;
        });

    assertThat(directoryRepository.find("delete-me@test.com", ORG_A)).isEmpty();
  }

  // ── 8. Rolled-back create leaves no row ──────────────────────────────────

  @Test
  void rolledBackCreateLeavesNoRow() {
    inA(
        () -> {
          transactionTemplate.executeWithoutResult(
              tx -> {
                portalContactService.createContact(
                    ORG_A,
                    customerA1,
                    "rolled-back@test.com",
                    "Rolled Back",
                    PortalContact.ContactRole.GENERAL);
                tx.setRollbackOnly();
              });
          return null;
        });

    assertThat(directoryRepository.find("rolled-back@test.com", ORG_A)).isEmpty();
    assertThat(
            inA(
                () ->
                    portalContactRepository.existsByEmailAndCustomerId(
                        "rolled-back@test.com", customerA1)))
        .isFalse();
  }

  // ── 10. /internal/portal/resync/{orgId} triggers a per-org reconcile ─────

  @Test
  void resyncEndpointRebuildsTheOrgsRowsAndDropsStaleOnes() throws Exception {
    createContactInA(customerA1, "resync-me@test.com");
    wipeOrgRows(ORG_A);
    directoryRepository.upsert("ghost-resync@test.com", ORG_A, schemaA, ORG_NAME_A);
    List<String> expected = inA(portalContactRepository::findDistinctActiveEmailsLower);

    mockMvc
        .perform(post("/internal/portal/resync/" + ORG_A).header("X-API-KEY", API_KEY))
        .andExpect(status().isOk());

    assertThat(directoryRepository.find("ghost-resync@test.com", ORG_A)).isEmpty();
    assertThat(expected).contains("resync-me@test.com");
    assertOrgRowsAre(ORG_A, schemaA, expected);
  }

  // ── 11. Multi-firm foundation: one email, two firms ───────────────────────

  @Test
  void sameEmailInTwoFirmsGivesTwoRowsAndArchivingInOneLeavesTheOther() {
    createContactInA(customerA1, "Multi.Firm@Test.com");
    var inFirmB =
        inB(
            () ->
                portalContactService.createContact(
                    ORG_B,
                    customerB1,
                    "multi.firm@test.com",
                    "Multi Firm",
                    PortalContact.ContactRole.GENERAL));

    List<PortalContactDirectoryEntry> both = directoryRepository.findAll("multi.firm@test.com");
    assertThat(both).extracting(PortalContactDirectoryEntry::orgId).containsExactly(ORG_A, ORG_B);
    assertThat(both)
        .extracting(PortalContactDirectoryEntry::orgName)
        .containsExactly(ORG_NAME_A, ORG_NAME_B);
    assertThat(both)
        .extracting(PortalContactDirectoryEntry::tenantSchema)
        .containsExactly(schemaA, schemaB);

    inB(() -> portalContactService.archiveContact(inFirmB.getId()));

    assertThat(directoryRepository.findAll("multi.firm@test.com"))
        .extracting(PortalContactDirectoryEntry::orgId)
        .containsExactly(ORG_A);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private PortalContact createContactInA(UUID customerId, String email) {
    return inA(
        () ->
            portalContactService.createContact(
                ORG_A, customerId, email, "Directory Contact", PortalContact.ContactRole.GENERAL));
  }

  private void wipeOrgRows(String orgId) {
    portalJdbc
        .sql("DELETE FROM portal.portal_contact_directory WHERE org_id = ?")
        .params(orgId)
        .update();
  }

  private void assertOrgRowsAre(String orgId, String schema, List<String> expectedEmails) {
    List<PortalContactDirectoryEntry> rows =
        portalJdbc
            .sql(
                "SELECT email_lower, org_id, tenant_schema, org_name, updated_at"
                    + " FROM portal.portal_contact_directory WHERE org_id = ?")
            .params(orgId)
            .query(PortalContactDirectoryEntry.class)
            .list();
    assertThat(rows)
        .extracting(PortalContactDirectoryEntry::emailLower)
        .containsExactlyInAnyOrderElementsOf(expectedEmails);
    assertThat(rows).allSatisfy(r -> assertThat(r.tenantSchema()).isEqualTo(schema));
  }

  private <T> T inA(Callable<T> action) {
    return inTenant(schemaA, ORG_A, action);
  }

  private <T> T inB(Callable<T> action) {
    return inTenant(schemaB, ORG_B, action);
  }

  private static <T> T inTenant(String schema, String orgId, Callable<T> action) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, schema)
          .where(RequestScopes.ORG_ID, orgId)
          .call(action::call);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
