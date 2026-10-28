package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the retention clock and the persisted per-matter retention end date
 * (ADR-249, Phase 88 U17 / ADR-354).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Completing a matter does NOT start the retention clock — only closure does.
 *   <li>Closing stamps {@code retention_clock_started_at = closed_at} and persists {@code
 *       retention_ends_on} = the UTC close date + the firm's years (seeded 5); the closure response
 *       and the matter detail both read that persisted value.
 *   <li>Reopening clears {@code retention_ends_on} but keeps the clock for audit.
 *   <li>Re-closing re-stamps both (fresh anchor).
 *   <li>A matter completed and later closed anchors on the close, not the completion.
 * </ul>
 *
 * <p>Provisioned as {@code legal-za} so the {@code matter_closure} module is enabled; the owner
 * holds the override capability, so every close uses {@code override=true} to bypass the gates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetentionClockIntegrationTest {
  private static final String ORG_ID = "org_retention_clock_test";

  private static final String OWNER_SUBJECT = "user_rc_owner";
  private static final JwtRequestPostProcessor OWNER_JWT =
      TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);

  /** The firm default seeded on every new tenant's org settings. */
  private static final int SEEDED_YEARS = 5;

  private static final String CLOSE_BODY =
      """
      {
        "reason": "CONCLUDED",
        "notes": "Retention clock test close.",
        "generateClosureLetter": false,
        "override": true,
        "overrideJustification": "Retention clock integration test: override used deliberately."
      }
      """;

  private static final String REOPEN_BODY =
      """
      {"notes": "Reopened by the retention clock test."}
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectRepository projectRepository;

  private String tenantSchema;

  @BeforeAll
  void provisionTenantAndOwner() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retention Clock Test Org", "legal-za");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER_SUBJECT, "rc_owner@test.com", "RC Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void completion_doesNotStartTheRetentionClock() throws Exception {
    String projectId =
        TestEntityHelper.createProject(mockMvc, OWNER_JWT, "RC complete only", "rc test");

    mockMvc
        .perform(patch("/api/matters/" + projectId + "/complete").with(OWNER_JWT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.retentionClockStartedAt").value(nullValue()))
        .andExpect(jsonPath("$.retentionEndsOn").value(nullValue()));

    Project project = load(projectId);
    assertThat(project.getRetentionClockStartedAt())
        .as("completion must not start the retention clock (ADR-354)")
        .isNull();
    assertThat(project.getRetentionEndsOn()).isNull();
    mockMvc
        .perform(get("/api/matters/" + projectId).with(OWNER_JWT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.retentionEndsOn").value(nullValue()));
  }

  @Test
  void close_stampsClockAndPersistsRetentionEndsOn() throws Exception {
    String projectId = TestEntityHelper.createProject(mockMvc, OWNER_JWT, "RC close", "rc test");

    String closeJson = close(projectId);

    Project project = load(projectId);
    assertThat(project.getClosedAt()).isNotNull();
    assertThat(project.getRetentionClockStartedAt()).isEqualTo(project.getClosedAt());
    LocalDate expected =
        project.getClosedAt().atZone(ZoneOffset.UTC).toLocalDate().plusYears(SEEDED_YEARS);
    assertThat(project.getRetentionEndsOn()).isEqualTo(expected);
    assertThat(JsonPath.<String>read(closeJson, "$.retentionEndsAt"))
        .as("the closure response reports the persisted end date")
        .isEqualTo(expected.toString());
    mockMvc
        .perform(get("/api/matters/" + projectId).with(OWNER_JWT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.retentionEndsOn").value(expected.toString()));
  }

  @Test
  void reopen_clearsRetentionEndsOn_keepsClock() throws Exception {
    String projectId = TestEntityHelper.createProject(mockMvc, OWNER_JWT, "RC reopen", "rc test");
    close(projectId);
    Instant clock = load(projectId).getRetentionClockStartedAt();
    assertThat(clock).isNotNull();

    reopen(projectId);

    Project project = load(projectId);
    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    assertThat(project.getRetentionEndsOn())
        .as("an open matter has no scheduled retention end")
        .isNull();
    assertThat(project.getRetentionClockStartedAt())
        .as("the clock is kept across reopen for audit")
        .isEqualTo(clock);
    mockMvc
        .perform(get("/api/matters/" + projectId).with(OWNER_JWT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.retentionEndsOn").value(nullValue()));
  }

  @Test
  void reClose_resetsClockAndRetentionEndsOn() throws Exception {
    String projectId = TestEntityHelper.createProject(mockMvc, OWNER_JWT, "RC re-close", "rc test");
    close(projectId);
    Instant firstClock = load(projectId).getRetentionClockStartedAt();

    // Ensure the second stamp is observably later.
    Thread.sleep(25);
    reopen(projectId);
    String secondCloseJson = close(projectId);

    Project project = load(projectId);
    assertThat(project.getRetentionClockStartedAt())
        .as("a re-close is a fresh anchor (ADR-249)")
        .isAfter(firstClock)
        .isEqualTo(project.getClosedAt());
    LocalDate expected =
        project.getClosedAt().atZone(ZoneOffset.UTC).toLocalDate().plusYears(SEEDED_YEARS);
    assertThat(project.getRetentionEndsOn()).isEqualTo(expected);
    assertThat(JsonPath.<String>read(secondCloseJson, "$.retentionEndsAt"))
        .isEqualTo(expected.toString());
  }

  @Test
  void completedThenClosed_anchorsOnClose_notOnCompletion() throws Exception {
    String projectId =
        TestEntityHelper.createProject(mockMvc, OWNER_JWT, "RC complete then close", "rc test");
    mockMvc
        .perform(patch("/api/matters/" + projectId + "/complete").with(OWNER_JWT))
        .andExpect(status().isOk());
    Instant completedAt = load(projectId).getCompletedAt();

    Thread.sleep(25);
    close(projectId);

    Project project = load(projectId);
    assertThat(project.getRetentionClockStartedAt())
        .as("the clock anchors on the close, not on the earlier completion")
        .isEqualTo(project.getClosedAt())
        .isAfter(completedAt);
    assertThat(project.getRetentionEndsOn())
        .isEqualTo(
            project.getClosedAt().atZone(ZoneOffset.UTC).toLocalDate().plusYears(SEEDED_YEARS));
  }

  // --- Helpers ---

  private String close(String projectId) throws Exception {
    return mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/close")
                .with(OWNER_JWT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CLOSE_BODY))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void reopen(String projectId) throws Exception {
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/reopen")
                .with(OWNER_JWT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REOPEN_BODY))
        .andExpect(status().isOk());
  }

  private Project load(String projectId) throws Exception {
    UUID id = UUID.fromString(projectId);
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(
            () ->
                projectRepository
                    .findById(id)
                    .orElseThrow(() -> new IllegalStateException("project not found: " + id)));
  }
}
