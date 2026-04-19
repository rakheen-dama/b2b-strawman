package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.event.StatementOfAccountGeneratedEvent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Controller-level integration tests for {@link StatementController} (Phase 67, Epic 491A, task
 * 491.6). Covers the happy path (201 with system template + summary + audit + event), capability
 * gating (403 for member role), module guard (403 on non-legal tenant), and an empty-period 201
 * with a zeroed summary. Pattern mirrors {@code DisbursementControllerIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, StatementControllerIntegrationTest.EventCapture.class})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementControllerIntegrationTest {

  private static final String LEGAL_ORG_ID = "org_statement_ctrl_legal";
  private static final String NON_LEGAL_ORG_ID = "org_statement_ctrl_nonlegal";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private EventCapture eventCapture;

  private String legalTenantSchema;
  private String nonLegalTenantSchema;
  private UUID legalOwnerMemberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    // --- Legal tenant: legal-za pack provisions the statement-of-account template. ---
    provisioningService.provisionTenant(LEGAL_ORG_ID, "Statement Ctrl Firm", "legal-za");

    legalOwnerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_statement_ctrl_owner",
                "statement_ctrl_owner@test.com",
                "Statement Ctrl Owner",
                "owner"));
    TestMemberHelper.syncMember(
        mockMvc,
        LEGAL_ORG_ID,
        "user_statement_ctrl_member",
        "statement_ctrl_member@test.com",
        "Statement Ctrl Member",
        "member");

    legalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();

    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer(
                          "Statement Ctrl Client", "stmt_ctrl@test.com", legalOwnerMemberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();

                  var project =
                      new Project(
                          "Statement Ctrl Matter",
                          "Matter for statement controller tests",
                          legalOwnerMemberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();
                }));

    // --- Non-legal tenant (disbursements module NOT enabled) ---
    provisioningService.provisionTenant(NON_LEGAL_ORG_ID, "Stmt NonLegal Firm", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NON_LEGAL_ORG_ID,
        "user_stmt_nonlegal_owner",
        "stmt_nonlegal_owner@test.com",
        "NonLegal Owner",
        "owner");
    nonLegalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(NON_LEGAL_ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, nonLegalTenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of());
                      orgSettingsRepository.save(settings);
                    }));
  }

  // ==========================================================================
  // Happy path: 201 with system template, GeneratedDocument + audit + event.
  // ==========================================================================

  @Test
  void legalTenant_owner_POST_returns201_withSystemTemplate_emitsEvent_andAudit() throws Exception {
    int eventsBefore = eventCapture.count.get();
    long auditsBefore = countAuditRowsInLegalTenant();

    String body =
        """
        {
          "periodStart": "2026-04-01",
          "periodEnd": "2026-04-30",
          "templateId": null
        }
        """;

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/statements")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_statement_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.templateId").isNotEmpty())
        .andExpect(jsonPath("$.htmlPreview").isNotEmpty())
        .andExpect(jsonPath("$.pdfUrl").isNotEmpty())
        .andExpect(jsonPath("$.matter.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.matter.name").value("Statement Ctrl Matter"))
        .andExpect(jsonPath("$.summary").exists())
        .andExpect(jsonPath("$.summary.totalFees").exists())
        .andExpect(jsonPath("$.summary.totalDisbursements").exists())
        .andExpect(jsonPath("$.summary.previousBalanceOwing").exists())
        .andExpect(jsonPath("$.summary.paymentsReceived").exists())
        .andExpect(jsonPath("$.summary.closingBalanceOwing").exists())
        .andExpect(jsonPath("$.summary.trustBalanceHeld").exists());

    // Domain event emitted exactly once.
    org.assertj.core.api.Assertions.assertThat(eventCapture.count.get())
        .isEqualTo(eventsBefore + 1);

    // Audit row inserted (statement.generated).
    long auditsAfter = countAuditRowsInLegalTenant();
    org.assertj.core.api.Assertions.assertThat(auditsAfter).isGreaterThan(auditsBefore);
  }

  // ==========================================================================
  // Empty period also returns 201 with a zeroed summary block.
  // ==========================================================================

  @Test
  void legalTenant_owner_POST_emptyPeriod_returns201_withZeroedSummary() throws Exception {
    // Period far in the past where the matter has nothing — sub-queries return empty/zero.
    String body =
        """
        {
          "periodStart": "2020-01-01",
          "periodEnd": "2020-01-31",
          "templateId": null
        }
        """;

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/statements")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_statement_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.summary.totalFees").value(0))
        .andExpect(jsonPath("$.summary.totalDisbursements").value(0))
        .andExpect(jsonPath("$.summary.previousBalanceOwing").value(0))
        .andExpect(jsonPath("$.summary.paymentsReceived").value(0))
        .andExpect(jsonPath("$.summary.closingBalanceOwing").value(0))
        .andExpect(jsonPath("$.summary.trustBalanceHeld").value(0));
  }

  // ==========================================================================
  // Member role has GENERATE_STATEMENT_OF_ACCOUNT (per arch §67.7 / V100 seed):
  // Owner = all 6, Admin = all except OVERRIDE_MATTER_CLOSURE,
  // Member = MANAGE_DISBURSEMENTS + GENERATE_STATEMENT_OF_ACCOUNT.
  // So a member should also be allowed to generate a statement (201).
  // ==========================================================================

  @Test
  void legalTenant_member_POST_returns201_capabilityGranted() throws Exception {
    String body =
        """
        {
          "periodStart": "2026-04-01",
          "periodEnd": "2026-04-30",
          "templateId": null
        }
        """;

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/statements")
                .with(TestJwtFactory.memberJwt(LEGAL_ORG_ID, "user_statement_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  // ==========================================================================
  // 403: disbursements module not enabled on the non-legal tenant (module guard).
  // ==========================================================================

  @Test
  void nonLegalTenant_owner_POST_returns403_fromModuleGuard() throws Exception {
    String body =
        """
        {
          "periodStart": "2026-04-01",
          "periodEnd": "2026-04-30",
          "templateId": null
        }
        """;

    mockMvc
        .perform(
            post("/api/matters/" + UUID.randomUUID() + "/statements")
                .with(TestJwtFactory.ownerJwt(NON_LEGAL_ORG_ID, "user_stmt_nonlegal_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("disbursements"));
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private long countAuditRowsInLegalTenant() {
    final long[] count = new long[1];
    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> count[0] = auditEventRepository.count()));
    return count[0];
  }

  private void runInLegalTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, legalTenantSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, legalOwnerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  /** Captures {@link StatementOfAccountGeneratedEvent} emissions for assertion. */
  @Component
  static class EventCapture {
    final AtomicInteger count = new AtomicInteger();

    @EventListener
    void on(StatementOfAccountGeneratedEvent event) {
      count.incrementAndGet();
    }
  }
}
