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
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
  private static final String SLUG = "section-35-data-pack";
  private static final String CLIENT = "Section35 Content Client";

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ReportDefinitionRepository reportDefinitionRepository;
  @Autowired private ReportExecutionService reportExecutionService;
  @Autowired private ReportRenderingService reportRenderingService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustTransactionRepository transactionRepository;

  private static final String FUND_LINE =
      "Legal Practitioners' Fidelity Fund — interest and charges";
  private static final String ACCOUNT = "S35 Content Trust Account";

  private String tenantSchema;
  private UUID memberId;
  private Map<String, Object> params;
  private String matchedSweepReference;
  private String unmatchedSweepReference;

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
                      "matterId": "%s",
                      "amount": 38019.15,
                      "reference": "DEP-S35-001",
                      "description": "Section 35 content deposit",
                      "transactionDate": "2025-06-01"
                    }
                    """
                        .formatted(clientId, trustMatterOf(clientId))))
        .andExpect(status().isCreated());

    // A payment out of trust (approved by a second member) so the pack carries a negative amount
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER, "s35_content_admin@test.com", "S35 Admin", "admin");
    var payment =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": 1000.00,
                          "reference": "PAY-S35-001",
                          "description": "Section 35 content payment",
                          "transactionDate": "2025-06-15"
                        }
                        """
                            .formatted(clientId, trustMatterOf(clientId))))
            .andExpect(status().isCreated())
            .andReturn();
    mockMvc
        .perform(
            post("/api/trust-transactions/" + TestEntityHelper.extractId(payment) + "/approve")
                .with(TestJwtFactory.adminJwt(ORG_ID, APPROVER)))
        .andExpect(status().isOk());

    // 650C: two Fund sweeps in the year — one recorded from an imported statement line (Rule
    // 54.14.16 evidence: file name + line number), one with no statement line
    var books = fixture.tenant(tenantSchema, ORG_ID, memberId);
    var matched =
        books.postNonClientDirect(trustAccountId, "FUND_SWEEP", "40.00", LocalDate.of(2025, 9, 30));
    books.bankLineFor(trustAccountId, matched, LocalDate.of(2025, 9, 30));
    var unmatched =
        books.postNonClientDirect(
            trustAccountId, "FUND_SWEEP", "25.00", LocalDate.of(2025, 10, 31));
    matchedSweepReference =
        books.read(() -> transactionRepository.findById(matched).orElseThrow().getReference());
    unmatchedSweepReference =
        books.read(() -> transactionRepository.findById(unmatched).orElseThrow().getReference());

    params = Map.of("trust_account_id", trustAccountId, "financial_year_end", "2026-03-31");
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

  /**
   * 650C (D-1, F-4): part (2) is the Rule 54.15 list as at the year end — the client (general +
   * invested), the Fund line (here in debit after the two sweeps) and their total, with the list's
   * comparison keys and the v3 keys; the note states the list's basis (not locked).
   */
  @Test
  @SuppressWarnings("unchecked")
  void trustCreditorsSection_isTheRule54_15List_withTheFundLine() {
    ReportResult result = execute();

    var section =
        result.rows().stream()
            .filter(row -> Section35DataPackQuery.CREDITORS_SECTION.equals(row.get("_section")))
            .toList();
    assertThat(section)
        .extracting(row -> row.get("label") + " " + row.get("amount"))
        .containsExactly(CLIENT + " 37019.15", FUND_LINE + " -65.00", "Total 36954.15");
    assertThat(result.rows()).noneMatch(row -> "Client Trust Balances".equals(row.get("_section")));

    var meta =
        ((List<Map<String, Object>>) result.summary().get("sections"))
            .stream()
                .filter(m -> Section35DataPackQuery.CREDITORS_SECTION.equals(m.get("sectionName")))
                .findFirst()
                .orElseThrow();
    assertThat(meta.get("rowCount")).isEqualTo(2);
    assertThat(meta.get("clientCount")).isEqualTo(1);
    assertThat((BigDecimal) meta.get("totalTrustBalance")).isEqualByComparingTo("37019.15");
    assertThat((BigDecimal) meta.get("creditorTotal")).isEqualByComparingTo("36954.15");
    assertThat((BigDecimal) meta.get("fundTotal")).isEqualByComparingTo("-65.00");
    assertThat(meta.get("fullyLocked")).isEqualTo(false);
    assertThat(meta.get("source")).isEqualTo("Not locked");
    assertThat(meta.get("note")).isEqualTo(ClientTrustBalancesQuery.LIVE_NOTE);
    assertThat(meta).doesNotContainKeys("accounts", "afterLock");
    assertThat((BigDecimal) result.summary().get("totalTrustBalanceAtYearEnd"))
        .isEqualByComparingTo("37019.15");
  }

  /**
   * 650C (§16.8.5 (6), D-13): each Fund sweep of the year with its statement file name and line
   * number (Rule 54.14.16 evidence), and the sweeps with no statement line in their own section.
   */
  @Test
  void fundSweeps_carryTheirStatementLine_andUnmatchedSweepsAreListedApart() {
    ReportResult result = execute();

    assertThat(sectionRows(result, Section35DataPackQuery.SWEEPS_SECTION))
        .extracting(row -> row.get("label") + " " + row.get("amount"))
        .containsExactly(
            "2025-09-30 · statement.csv · line 1 · " + matchedSweepReference + " -40.00",
            "Total -40.00");
    assertThat(sectionRows(result, Section35DataPackQuery.UNMATCHED_SWEEPS_SECTION))
        .extracting(row -> row.get("label") + " " + row.get("amount"))
        .containsExactly(
            "2025-10-31 · " + unmatchedSweepReference + " — no statement line -25.00",
            "Total -25.00");
  }

  /**
   * 650C (§16.8.5 (5), D-12): the Rule 54.28 schedule for the year replaces interest allocation —
   * year totals per account, projector-less (no Total row), the Fund ledger b/f + movements = c/f.
   */
  @Test
  void schedulePart_replacesInterestAllocation_withYearTotalsPerAccount() {
    ReportResult result = execute();

    var schedule = sectionRows(result, Section35DataPackQuery.SCHEDULE_SECTION);
    var byLabel =
        schedule.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    r -> (String) r.get("label"), r -> (BigDecimal) r.get("amount")));
    assertThat(byLabel.get(ACCOUNT + " — Fund ledger brought forward")).isEqualByComparingTo("0");
    assertThat(byLabel.get(ACCOUNT + " — paid to the Fund")).isEqualByComparingTo("65.00");
    assertThat(byLabel.get(ACCOUNT + " — Fund ledger carried forward"))
        .isEqualByComparingTo("-65.00");
    assertThat(schedule).noneMatch(r -> "Total".equals(r.get("label")));
    assertThat(result.rows()).noneMatch(r -> "Interest Allocation".equals(r.get("_section")));
    assertThat(result.rows())
        .extracting(r -> (String) r.get("_section"))
        .allSatisfy(section -> assertThat(section).doesNotMatch(".*\\b[A-Z]{2,}_[A-Z_]{2,}\\b.*"));
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
        .contains(TrustReportWords.TRUST_ASSETS_NOTE.replace("'", "&#39;"))
        .contains("statement.csv · line 1")
        .contains(Section35DataPackQuery.SCHEDULE_SECTION)
        .containsPattern("38[\\s,.\\u00a0]?019[.,]15")
        .doesNotContain("Rows:");
  }

  /** G89-F48: the PDF printed raw money ("38019.15"); it prints rand, grouped, comma decimals. */
  @Test
  void pdf_formatsAmountsAsRand() {
    String html =
        runInTenantReturning(
            () -> {
              var definition = reportDefinitionRepository.findBySlug(SLUG).orElseThrow();
              return reportRenderingService.renderHtml(definition, execute(), params);
            });

    assertThat(html)
        .contains(">R 38 019,15<")
        .doesNotContain("38019.15")
        .as("a payment is a negative rand amount")
        .containsPattern(">-R \\d");
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

  private static List<Map<String, Object>> sectionRows(ReportResult result, String section) {
    return result.rows().stream().filter(row -> section.equals(row.get("_section"))).toList();
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

  private final java.util.Map<String, UUID> trustMatters = new java.util.HashMap<>();

  /** G89-F28: a client's trust postings sit on a matter of its own (one per client). */
  private UUID trustMatterOf(String clientId) {
    return trustMatters.computeIfAbsent(
        clientId,
        id ->
            TestTrustMatterHelper.openClientMatter(
                tenantSchema,
                transactionTemplate,
                projectRepository,
                UUID.fromString(id),
                "Trust matter " + id,
                memberId));
  }
}
