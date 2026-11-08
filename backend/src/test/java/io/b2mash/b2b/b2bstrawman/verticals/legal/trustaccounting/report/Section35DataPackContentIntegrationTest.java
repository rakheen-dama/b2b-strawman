package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.reporting.ReportExecutionService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportRenderingService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * G89-F18 (re-opens G88-F40): the Section 35 data pack must carry its data in the columns it
 * declares ({@code _section}, {@code label}, {@code amount}) — in the result rows, the CSV and the
 * PDF. Before the fix only the reconciliation rows had {@code label} / {@code amount}: every other
 * cell rendered "— —", the CSV had a blank Amount column and the PDF printed section names and row
 * counts only. Driven through the installed {@code section-35-data-pack} definition (legal-za
 * tenant) and the real execution + rendering services.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Section35DataPackContentIntegrationTest {

  private static final String ORG_ID = "org_section35_content";
  private static final String OWNER = "user_s35_content_owner";
  private static final String APPROVER = "user_s35_content_admin";
  private static final String INTEREST_SECTION = "Interest Allocation";
  private static final String SLUG = "section-35-data-pack";
  private static final String CLIENT = "Section35 Content Client";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ReportDefinitionRepository reportDefinitionRepository;
  @Autowired private ReportExecutionService reportExecutionService;
  @Autowired private ReportRenderingService reportRenderingService;

  private String tenantSchema;
  private UUID memberId;
  private Map<String, Object> params;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Section 35 Content Firm", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "s35_content@test.com", "S35 Owner", "owner"));
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("trust_accounting"));
                  orgSettingsRepository.save(settings);
                }));

    var jwt = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    var account =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "S35 Content Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000003535",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2025-04-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    UUID trustAccountId = UUID.fromString(TestEntityHelper.extractId(account));
    String clientId = TestEntityHelper.createCustomer(mockMvc, jwt, CLIENT, "s35_client@test.com");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "amount": 38019.15,
                      "reference": "DEP-S35-001",
                      "description": "Section 35 content deposit",
                      "transactionDate": "2025-06-01"
                    }
                    """
                        .formatted(clientId)))
        .andExpect(status().isCreated());

    // G89-F50: two POSTED interest runs inside FY 2025/26 plus one calculated-but-unposted run.
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER, "s35_content_admin@test.com", "S35 Admin", "admin");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/lpff-rates")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"effectiveFrom": "2025-04-01", "ratePercent": 0.0750,
                     "lpffSharePercent": 0.7500, "notes": "S35 content rate"}
                    """))
        .andExpect(status().isCreated());
    postedRun(trustAccountId, "2025-07-01", "2025-07-31", true);
    postedRun(trustAccountId, "2025-08-01", "2025-08-31", true);
    postedRun(trustAccountId, "2025-09-01", "2025-09-30", false);

    params = Map.of("trust_account_id", trustAccountId, "financial_year_end", "2026-03-31");
  }

  @Test
  void interestSection_listsEveryPostedRunInTheYear_withOneTotal() {
    ReportResult result = execute();

    var interestRows =
        result.rows().stream()
            .filter(row -> INTEREST_SECTION.equals(row.get("_section")))
            .filter(row -> row.get("_total") == null)
            .toList();
    assertThat(interestRows)
        .as("one row per client per POSTED run in the financial year")
        .hasSize(2)
        .extracting(row -> String.valueOf(row.get("label")))
        .anySatisfy(label -> assertThat(label).contains(CLIENT).contains("2025-07-31"))
        .anySatisfy(label -> assertThat(label).contains(CLIENT).contains("2025-08-31"))
        .noneSatisfy(label -> assertThat(label).contains("2025-09-30"));

    var totals =
        result.rows().stream()
            .filter(row -> INTEREST_SECTION.equals(row.get("_section")))
            .filter(row -> Boolean.TRUE.equals(row.get("_total")))
            .toList();
    assertThat(totals).hasSize(1);
    BigDecimal sum =
        interestRows.stream()
            .map(row -> (BigDecimal) row.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isPositive();
    assertThat((BigDecimal) totals.getFirst().get("amount")).isEqualByComparingTo(sum);
  }

  @Test
  void everyRow_hasLabelAndAmount() {
    ReportResult result = execute();

    assertThat(result.rows()).isNotEmpty();
    assertThat(result.rows())
        .allSatisfy(
            row -> {
              assertThat(row.get("_section")).as("_section of " + row).isNotNull();
              assertThat(row.get("label")).as("label of " + row).isNotNull();
              assertThat(row.get("amount")).as("amount of " + row).isNotNull();
            });
    assertThat(result.rows())
        .extracting(row -> String.valueOf(row.get("label")))
        .anySatisfy(label -> assertThat(label).contains("DEP-S35-001").contains(CLIENT))
        .anySatisfy(label -> assertThat(label).isEqualTo(CLIENT));
  }

  @Test
  void pdf_containsRowLabels_notOnlyCounts() {
    String html =
        runInTenantReturning(
            () -> {
              var definition = reportDefinitionRepository.findBySlug(SLUG).orElseThrow();
              return reportRenderingService.renderHtml(definition, execute(), params);
            });

    assertThat(html)
        .as("the PDF template prints each section's rows, not just its row count")
        .contains("DEP-S35-001")
        .contains(CLIENT)
        .containsPattern("38[\\s,.\\u00a0]?019[.,]15")
        .doesNotContain("Rows:");
  }

  @Test
  void csv_hasNoBlankAmountColumn() {
    String csv =
        runInTenantReturning(
            () -> {
              var definition = reportDefinitionRepository.findBySlug(SLUG).orElseThrow();
              var out = new ByteArrayOutputStream();
              try {
                reportRenderingService.writeCsv(definition, execute(), params, out);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
              return out.toString(StandardCharsets.UTF_8);
            });

    List<String> dataLines =
        csv.lines().filter(l -> !l.startsWith("#")).skip(1).filter(l -> !l.isBlank()).toList();
    assertThat(dataLines).isNotEmpty();
    assertThat(dataLines)
        .as("every CSV row has an Item and an Amount")
        .allSatisfy(
            line -> {
              String[] cells = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
              assertThat(cells).as(line).hasSize(3);
              assertThat(cells[1]).as("Item of " + line).isNotBlank();
              assertThat(cells[2]).as("Amount of " + line).isNotBlank();
            });
  }

  // ---------- helpers ----------

  /** Creates and calculates an interest run; approves (as the admin) and posts it if asked. */
  private void postedRun(UUID trustAccountId, String start, String end, boolean post)
      throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    var created =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/interest-runs")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"periodStart": "%s", "periodEnd": "%s"}
                        """
                            .formatted(start, end)))
            .andExpect(status().isCreated())
            .andReturn();
    String runId = TestEntityHelper.extractId(created);
    mockMvc
        .perform(post("/api/interest-runs/" + runId + "/calculate").with(owner))
        .andExpect(status().isOk());
    if (!post) {
      return;
    }
    mockMvc
        .perform(
            post("/api/interest-runs/" + runId + "/approve")
                .with(TestJwtFactory.adminJwt(ORG_ID, APPROVER)))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/interest-runs/" + runId + "/post").with(owner))
        .andExpect(status().isOk());
  }

  private ReportResult execute() {
    return runInTenantReturning(() -> reportExecutionService.executeForExport(SLUG, params));
  }

  private void runInTenant(Runnable action) {
    var scope =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID);
    if (memberId != null) {
      scope =
          scope
              .where(RequestScopes.MEMBER_ID, memberId)
              .where(RequestScopes.ORG_ROLE, "owner")
              .where(RequestScopes.CAPABILITIES, Set.copyOf(Capability.ALL_NAMES));
    }
    scope.run(action);
  }

  private <T> T runInTenantReturning(Supplier<T> action) {
    Object[] holder = new Object[1];
    runInTenant(() -> holder[0] = action.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }
}
