package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Rendering-level regression tests for LZKC-007 / LZKC-017 (part 1) on the Statement of Account
 * pipeline ({@code StatementService} → {@code StatementOfAccountContextBuilder} → {@code
 * TiptapRenderer}).
 *
 * <ul>
 *   <li><b>Locale format hints (LZKC-017):</b> standalone amount variables (fees totals, summary
 *       block) historically rendered raw {@code BigDecimal.toString()} ("1250.00") because {@code
 *       StatementService.renderHtml} passed an empty {@code formatHints} map, while loop-table
 *       columns rendered ZA-locale currency ("R50&nbsp;000,00") via per-column format attrs — mixed
 *       number locales in one document. Standalone dates had the same gap ("2026-04-01" vs "1 April
 *       2026").
 *   <li><b>Letterhead logo (LZKC-007):</b> {@code TemplateContextHelper.buildOrgContext} presigns
 *       {@code org.logoUrl} from branding, but no generated-document template emitted it — the
 *       rendered statement had no letterhead. The template now carries an {@code org.logoUrl}
 *       variable rendered through the {@code image} format hint; absent logo renders gracefully.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementRenderingIntegrationTest {

  private static final String ORG_ID = "org_statement_rendering_test";
  private static final String LOGO_S3_KEY = "org/statement-rendering-test/branding/logo.png";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private StatementOfAccountContextBuilder statementContextBuilder;

  private String tenantSchema;
  private UUID memberId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Statement Rendering Firm", "legal-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_stmt_render_owner",
                "stmt_render_owner@test.com",
                "Stmt Render Owner",
                "owner"));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  // trust_accounting added for the LZKC-030 repro below; with no trust activity
                  // for this class's shared customer, the other tests' trust block stays empty
                  // exactly as it was when only `disbursements` was enabled.
                  settings.setEnabledModules(List.of("disbursements", "trust_accounting"));
                  settings.updateCurrency("ZAR");
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer(
                          "Rendering Test Client", "stmt_render@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);

                  var project =
                      new Project("Rendering Test Matter", "Locale + letterhead", memberId);
                  project.setCustomerId(customer.getId());
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();

                  var task =
                      new Task(
                          projectId, "Drafting", "Drafting work", "MEDIUM", "TASK", null, memberId);
                  task = taskRepository.saveAndFlush(task);

                  // 60 billable minutes at R1250/h -> fee line amount 1250.00
                  var entry =
                      new TimeEntry(
                          task.getId(),
                          memberId,
                          LocalDate.of(2026, 4, 15),
                          60,
                          true,
                          null,
                          "Drafting heads of argument");
                  entry.snapshotBillingRate(new BigDecimal("1250.00"), "ZAR");
                  timeEntryRepository.saveAndFlush(entry);
                }));
  }

  @Test
  void standaloneAmountsAndDatesRenderInTenantLocale_matchingLoopTables() throws Exception {
    String html = generateStatementHtml();

    // Loop-table cells already rendered ZA currency; the standalone totals must match.
    // 60 min @ R1250/h -> "R1 250,00" (NBSP grouping, comma decimal), not raw "1250.00".
    assertThat(html).contains("R1&nbsp;250,00");
    assertThat(html).doesNotContain(">1250.00<");

    // Standalone dates get the same "d MMMM yyyy" treatment as loop-table date columns.
    assertThat(html).contains("1 April 2026");
    assertThat(html).contains("30 April 2026");
    assertThat(html).doesNotContain("2026-04-01");
  }

  @Test
  void statementRendersLetterheadLogo_whenBrandingLogoConfigured() throws Exception {
    setLogoS3Key(LOGO_S3_KEY);

    String html = generateStatementHtml();

    assertThat(html).contains("<img class=\"letterhead-logo\"");
    assertThat(html).contains("http://test-storage/test-bucket/" + LOGO_S3_KEY);
  }

  @Test
  void statementRendersCleanly_whenNoBrandingLogo() throws Exception {
    setLogoS3Key(null);

    String html = generateStatementHtml();

    assertThat(html).doesNotContain("<img");
    assertThat(html).contains("Statement of Account");
  }

  /**
   * LZKC-030 (QA Day 61): a matter whose FIRST trust deposit falls ON the statement period start
   * date must print a DB-true opening balance that EXCLUDES that deposit — the deposit is already
   * itemised in the Deposits table, so counting it in the opening double-counts it and the Section
   * 86 statement stops self-reconciling (printed: opening R50 000 + deposits − payments ≠ closing).
   *
   * <p>DB-true reproduction: seeds a real trust account + a real R50 000 deposit dated exactly on
   * the period start via the API, then builds the SoA context through the full real stack (builder
   * → ClientLedgerService → TrustTransactionRepository → Postgres).
   */
  @Test
  void trustOpeningBalance_excludesDepositOnPeriodStartDate_andSelfReconciles() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_stmt_render_owner");

    // Dedicated customer + matter so this test's trust activity cannot leak into the shared
    // rendering fixtures used by the locale/letterhead tests above.
    UUID[] ids = new UUID[2]; // [0] customerId, [1] projectId
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomer("Dlamini", "dlamini_trust@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);
                  ids[0] = customer.getId();

                  var project = new Project("Dlamini v RAF", "Trust boundary matter", memberId);
                  project.setCustomerId(customer.getId());
                  project = projectRepository.saveAndFlush(project);
                  ids[1] = project.getId();
                }));

    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "LZKC-030 Trust Account",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000030",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String trustAccountId = TestEntityHelper.extractId(accountResult);

    // The deposit lands exactly ON the statement period start date (2026-04-01).
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 50000.00,
                      "reference": "DEP/2026/001",
                      "description": "Initial RAF settlement deposit",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(ids[0])))
        .andExpect(status().isCreated());

    Map<String, Object> context =
        runInTenantAndGet(
            () ->
                statementContextBuilder.build(
                    ids[1], LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)));

    @SuppressWarnings("unchecked")
    Map<String, Object> trust = (Map<String, Object>) context.get("trust");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> deposits = (List<Map<String, Object>>) trust.get("deposits");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> payments = (List<Map<String, Object>>) trust.get("payments");

    // The period-start-day deposit is itemised exactly once.
    assertThat(deposits).hasSize(1);
    assertThat(payments).isEmpty();

    BigDecimal opening = (BigDecimal) trust.get("opening_balance");
    BigDecimal closing = (BigDecimal) trust.get("closing_balance");
    assertThat(opening)
        .as("DB-true opening before 2026-04-01 is R0 — the same-day deposit must not be counted")
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(closing).isEqualByComparingTo(new BigDecimal("50000.00"));

    // Self-reconciliation invariant: opening + Σdeposits − Σpayments == closing.
    BigDecimal depositTotal =
        deposits.stream()
            .map(d -> new BigDecimal(d.get("amount").toString()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(opening.add(depositTotal)).isEqualByComparingTo(closing);
  }

  // ---------- helpers ----------

  private void setLogoS3Key(String s3Key) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.getBranding().setLogoS3Key(s3Key);
                  orgSettingsRepository.save(settings);
                }));
  }

  private String generateStatementHtml() throws Exception {
    String body =
        """
        {
          "periodStart": "2026-04-01",
          "periodEnd": "2026-04-30",
          "templateId": null
        }
        """;

    var result =
        mockMvc
            .perform(
                post("/api/matters/" + projectId + "/statements")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_stmt_render_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    var json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("htmlPreview").asText();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  private <T> T runInTenantAndGet(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(action::get);
  }
}
