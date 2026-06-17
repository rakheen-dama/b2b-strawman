package io.b2mash.b2b.b2bstrawman.mcp.consent;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
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
 * Integration tests for the V129 {@code mcp_egress_consents} migration and the {@link
 * McpEgressConsent} entity (Epic 565A). Mirrors the {@code V31MigrationTest} harness: provisioning
 * the tenant proves V129 runs clean per-schema, then entity round-trip / append-only /
 * latest-decision are asserted inside the bound tenant scope.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpEgressConsentTest {

  private static final String ORG_ID = "org_mcp_consent_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private McpEgressConsentRepository repository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP Consent Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  // ---- (1) migration runs clean + a row persists -----------------------------

  @Test
  void migrationRunsCleanAndRowPersists() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var saved =
                      repository.saveAndFlush(McpEgressConsent.grant(memberId, "popia-egress-v1"));
                  assertThat(saved.getId()).isNotNull();
                  assertThat(repository.findById(saved.getId())).isPresent();
                }));
  }

  // ---- (2) entity round-trip -------------------------------------------------

  @Test
  void entityRoundTripPreservesFields() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var saved =
                      repository.saveAndFlush(McpEgressConsent.grant(memberId, "popia-egress-v1"));
                  var found = repository.findById(saved.getId()).orElseThrow();
                  assertThat(found.getConsentedBy()).isEqualTo(memberId);
                  assertThat(found.getConsentVersion()).isEqualTo("popia-egress-v1");
                  assertThat(found.getAction()).isEqualTo("GRANTED");
                  assertThat(found.isGranted()).isTrue();
                  assertThat(found.getConsentedAt()).isNotNull();
                  assertThat(found.getCreatedAt()).isNotNull();
                }));
  }

  // ---- (3) append-only: grant -> revoke -> re-grant = 3 rows ------------------

  @Test
  void historyIsAppendOnly() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  repository.deleteAll();
                  var granted =
                      repository.saveAndFlush(McpEgressConsent.grant(memberId, "popia-egress-v1"));
                  repository.saveAndFlush(McpEgressConsent.revoke(memberId, "popia-egress-v1"));
                  repository.saveAndFlush(McpEgressConsent.grant(memberId, "popia-egress-v1"));

                  assertThat(repository.count()).isEqualTo(3);
                  // The first GRANTED row is unchanged (still GRANTED, same id).
                  var firstReloaded = repository.findById(granted.getId()).orElseThrow();
                  assertThat(firstReloaded.getAction()).isEqualTo("GRANTED");
                }));
  }

  // ---- (4) latest-decision lookup returns the newest row by consented_at -----

  @Test
  void latestDecisionLookupReturnsNewestRow() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  repository.deleteAll();
                  // saveAndFlush in sequence; consentedAt is Instant.now() per construction.
                  repository.saveAndFlush(McpEgressConsent.grant(memberId, "popia-egress-v1"));
                  sleepMillis();
                  var revoked =
                      repository.saveAndFlush(McpEgressConsent.revoke(memberId, "popia-egress-v1"));

                  var latest =
                      repository.findTopByOrderByConsentedAtDescCreatedAtDesc().orElseThrow();
                  assertThat(latest.getId()).isEqualTo(revoked.getId());
                  assertThat(latest.getAction()).isEqualTo("REVOKED");
                  assertThat(latest.isGranted()).isFalse();
                }));
  }

  private static void sleepMillis() {
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
