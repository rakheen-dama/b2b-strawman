package io.b2mash.b2b.b2bstrawman.portal.directory;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
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

/**
 * Phase 88 U11 (ADR-351, §13.7.2): {@link PortalContactDirectoryBackfillRunner} rebuilds one
 * tenant's directory rows and records its marker once. Uses the package-private {@code
 * backfillTenant} seam on this class's own tenant, so the assertions never depend on the other
 * tenants accumulated in the shared test database; one {@code run(null)} smoke check covers the
 * runner's wiring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalContactDirectoryBackfillIntegrationTest {

  private static final String ORG_ID = "org_contact_directory_backfill";
  private static final String ORG_NAME = "Directory Backfill Firm";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private PortalContactDirectoryRepository directoryRepository;
  @Autowired private PortalContactDirectoryBackfillRunner backfillRunner;

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private OrgSchemaMapping mapping;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, ORG_NAME, null);
    UUID memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_dir_backfill_owner",
                "dir_backfill_owner@test.com",
                "Directory Backfill Owner",
                "owner"));
    mapping = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    customerId =
        inTenant(
            () ->
                customerService
                    .createCustomer(
                        "Backfill Customer", "backfill-cust@test.com", null, null, null, memberId)
                    .getId());
  }

  @Test
  void backfillTenantRestoresRowsOnceAndIsIdempotent() {
    createContact("Backfill.Me@Test.com");
    var archived = createContact("backfill-archived@test.com");
    inTenant(() -> portalContactService.archiveContact(archived.getId()));
    resetTenant();
    List<String> expected = inTenant(portalContactRepository::findDistinctActiveEmailsLower);
    assertThat(expected)
        .contains("backfill.me@test.com")
        .doesNotContain("backfill-archived@test.com");

    int firstRows = backfillRunner.backfillTenant(mapping);

    assertThat(firstRows).isEqualTo(expected.size());
    assertOrgRowsAre(expected);
    Map<String, Object> markerAfterFirst = marker();
    assertThat(((Number) markerAfterFirst.get("row_count")).intValue()).isEqualTo(expected.size());

    int secondRows = backfillRunner.backfillTenant(mapping);

    assertThat(secondRows).isEqualTo(-1); // marker present → skipped
    assertThat(marker()).isEqualTo(markerAfterFirst);
    assertOrgRowsAre(expected);
  }

  @Test
  void runSmokeCheckBackfillsAnUnmarkedTenant() {
    createContact("backfill-run@test.com");
    resetTenant();
    List<String> expected = inTenant(portalContactRepository::findDistinctActiveEmailsLower);

    backfillRunner.run(null);

    assertOrgRowsAre(expected);
    assertThat(((Number) marker().get("row_count")).intValue()).isEqualTo(expected.size());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private PortalContact createContact(String email) {
    return inTenant(
        () ->
            portalContactService.createContact(
                ORG_ID, customerId, email, "Backfill Contact", PortalContact.ContactRole.GENERAL));
  }

  /** Simulates a tenant that predates V2: no directory rows, no marker. */
  private void resetTenant() {
    portalJdbc
        .sql("DELETE FROM portal.portal_contact_directory WHERE org_id = ?")
        .params(ORG_ID)
        .update();
    portalJdbc
        .sql("DELETE FROM portal.portal_contact_directory_backfill WHERE tenant_schema = ?")
        .params(mapping.getSchemaName())
        .update();
  }

  private void assertOrgRowsAre(List<String> expectedEmails) {
    List<PortalContactDirectoryEntry> rows =
        portalJdbc
            .sql(
                "SELECT email_lower, org_id, tenant_schema, org_name, updated_at"
                    + " FROM portal.portal_contact_directory WHERE org_id = ?")
            .params(ORG_ID)
            .query(PortalContactDirectoryEntry.class)
            .list();
    assertThat(rows)
        .extracting(PortalContactDirectoryEntry::emailLower)
        .containsExactlyInAnyOrderElementsOf(expectedEmails);
    assertThat(rows)
        .allSatisfy(
            r -> {
              assertThat(r.tenantSchema()).isEqualTo(mapping.getSchemaName());
              assertThat(r.orgName()).isEqualTo(ORG_NAME);
            });
  }

  private Map<String, Object> marker() {
    return portalJdbc
        .sql(
            "SELECT row_count, backfilled_at FROM portal.portal_contact_directory_backfill"
                + " WHERE tenant_schema = ?")
        .params(mapping.getSchemaName())
        .query()
        .singleRow();
  }

  private <T> T inTenant(Callable<T> action) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
          .where(RequestScopes.ORG_ID, ORG_ID)
          .call(action::call);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
