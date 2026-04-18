package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the retention-clock anchor (ADR-249 minimal slice, GAP-L-08).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>First ACTIVE → COMPLETED transition stamps {@code retention_clock_started_at}.
 *   <li>Re-completion after reopen preserves the earliest stamp (never overwritten).
 *   <li>Active projects have a null retention clock.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetentionClockIntegrationTest {
  private static final String ORG_ID = "org_retention_clock_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectRepository projectRepository;

  private static final String OWNER_SUBJECT = "user_rc_owner";
  private static final JwtRequestPostProcessor OWNER_JWT =
      TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);

  private String tenantSchema;

  @BeforeAll
  void provisionTenantAndOwner() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retention Clock Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER_SUBJECT, "rc_owner@test.com", "RC Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void retentionClockStartedAt_setsOnFirstCompletion() throws Exception {
    String projectId =
        TestEntityHelper.createProject(
            mockMvc, OWNER_JWT, "Retention clock stamp-on-complete", "retention clock test");

    // Clock is null while ACTIVE.
    assertThat(loadRetentionClock(projectId)).isNull();

    Instant beforeComplete = Instant.now();
    completeProject(projectId);
    Instant afterComplete = Instant.now();

    Instant clock = loadRetentionClock(projectId);
    assertThat(clock).isNotNull();
    // Stamp lies between before- and after-complete timestamps (within a small skew tolerance).
    assertThat(clock).isBetween(beforeComplete.minusMillis(10), afterComplete.plusMillis(10));
  }

  @Test
  void retentionClockStartedAt_notOverwrittenOnReComplete() throws Exception {
    String projectId =
        TestEntityHelper.createProject(
            mockMvc, OWNER_JWT, "Retention clock preserved on re-complete", "retention clock test");

    completeProject(projectId);
    Instant t0 = loadRetentionClock(projectId);
    assertThat(t0).isNotNull();

    // Ensure any second stamp would be observably later.
    Thread.sleep(25);

    reopenProject(projectId);
    completeProject(projectId);

    Instant t1 = loadRetentionClock(projectId);
    assertThat(t1)
        .as("retention clock must not be overwritten by subsequent completions")
        .isEqualTo(t0);
  }

  // The "null while ACTIVE" case is covered by the first assertion in
  // retentionClockStartedAt_setsOnFirstCompletion above — a standalone test duplicates that
  // assertion at the cost of another tenant-scoped API round-trip.

  // --- Helpers ---

  private void completeProject(String projectId) throws Exception {
    mockMvc
        .perform(patch("/api/projects/" + projectId + "/complete").with(OWNER_JWT))
        .andExpect(status().isOk());
  }

  private void reopenProject(String projectId) throws Exception {
    mockMvc
        .perform(patch("/api/projects/" + projectId + "/reopen").with(OWNER_JWT))
        .andExpect(status().isOk());
  }

  private Instant loadRetentionClock(String projectId) throws Exception {
    UUID id = UUID.fromString(projectId);
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(
            () ->
                projectRepository
                    .findById(id)
                    .orElseThrow(() -> new IllegalStateException("project not found: " + id))
                    .getRetentionClockStartedAt());
  }
}
