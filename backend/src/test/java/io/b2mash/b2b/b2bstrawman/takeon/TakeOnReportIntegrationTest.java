package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.C;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.Accounts;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.Validated;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnRowCommitters;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.thymeleaf.TemplateEngine;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 93 657C (task 657.22, 657.24, §17.3.12, ADR-391, FD-15): the take-on report written by the
 * trust commit — its creditor list is {@code liveList(G, C)} line for line, both sides of the
 * reconciled position agree to the cent, the sign-off section names the preparer and the signer,
 * the row is immutable and its SHA-256 recomputes from the stored jsonb, the JSON endpoints are
 * {@code VIEW_TRUST}-gated, and a client's later anonymisation redacts the take-on rows but never
 * the report (R-4).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnReportIntegrationTest {

  private static final String ORG_ID = "org_takeon_report_test";
  private static final String OWNER = "user_tor_owner";
  private static final String TAKE_ON_ONLY = "user_tor_takeon_only";
  private static final String TRUST_READER = "user_tor_trust_reader";
  private static final LocalDate CUTOVER = LocalDate.parse(C);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustMonthlyListService monthlyLists;
  @Autowired private TakeOnReportRepository reports;
  @Autowired private EntityManager entityManager;
  @Autowired private TakeOnReportRenderer renderer;
  @Autowired private TakeOnReportFileRepository reportFiles;
  @Autowired private TakeOnReportRenderRepository reportRenders;
  @Autowired private StorageService storage;
  @Autowired private TemplateEngine templateEngine;
  @Autowired private PdfRenderingService pdfRenderingService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private Clock clock;
  @Autowired private TakeOnBatchRepository batchRepository;
  @Autowired private TakeOnRowRunner rowRunner;
  @Autowired private TakeOnRowCommitters committers;
  @Autowired private TakeOnDrainSweep sweep;
  @Autowired private OrgSchemaMappingRepository mappings;

  private String schema;
  private TrustBooksFixture.Tenant books;
  private TakeOnTrustWorld world;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService.provisionTenant(ORG_ID, "Report Attorneys", "legal-za").schemaName();
    UUID ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tor_owner@test.com", "Report Owner", "owner"));
    UUID takeOnOnly =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, TAKE_ON_ONLY, "tor_only@test.com", "Take-on Only", "member"));
    books = fixture.tenant(schema, ORG_ID, ownerId);
    world =
        new TakeOnTrustWorld(
            mockMvc, jdbc, commitService, drain, ORG_ID, OWNER, schema, ownerId, "tor");
    assignRole(takeOnOnly, createRole("Take-on without trust", "MANAGE_TAKE_ON"));
    UUID trustReader =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, TRUST_READER, "tor_reader@test.com", "Trust Reader", "member"));
    assignRole(trustReader, createRole("Trust reader", "VIEW_TRUST"));
    world.takeOnClientsAndMatters(List.of("A", "B", "C", "Z"));
  }

  // 1
  @Test
  void theSnapshotIsTheLiveListAtTheCutover_andBothSidesAgreeToTheCent() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.committed(acc);
    String report = reportJson(reportId(batch));

    // the creditor list is liveList(G, C), line for line
    String gName = accountName(acc.g());
    var live = books.read(() -> monthlyLists.liveList(acc.g(), CUTOVER));
    var expectedLines =
        JSON.convertValue(TakeOnReportSnapshot.section(gName, live).lines(), List.class);
    List<Object> lines = JsonPath.read(report, "$.snapshot.creditorList.lines");
    assertThat(lines).isEqualTo(expectedLines);
    assertThat((String) JsonPath.read(report, "$.snapshot.creditorList.creditorTotal"))
        .isEqualTo("403500.00");
    assertThat((List<String>) JsonPath.read(report, "$.snapshot.creditorList.lines[*].kind"))
        .contains("CLIENT", "FUND", "UNIDENTIFIED", "INTER_ACCOUNT");
    assertThat(
            (List<String>) JsonPath.read(report, "$.snapshot.investmentAccounts[*].trustAccountId"))
        .containsExactlyInAnyOrder(acc.i3().toString(), acc.i4().toString());

    // previous system vs Kazi: every difference R0,00
    Map<String, Object> previous = JsonPath.read(report, "$.snapshot.previousSystem");
    Map<String, Object> kazi = JsonPath.read(report, "$.snapshot.kazi");
    assertThat(previous)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "bankBalance", "418500.00",
                "outstandingDeposits", "5000.00",
                "outstandingPayments", "20000.00",
                "adjustedBank", "403500.00",
                "cashbook", "403500.00",
                "fundLedger", "1250.00",
                "investedTotal", "300000.00"));
    assertThat(kazi).isEqualTo(previous);
    assertThat(((Map<String, Object>) JsonPath.read(report, "$.snapshot.differences")).values())
        .containsOnly("0.00");

    // the items, the investments, the flags, the sign-off, the sources
    assertThat((List<String>) JsonPath.read(report, "$.snapshot.unidentified[*].receivedDate"))
        .containsExactlyInAnyOrder("2025-06-15", "2026-09-03");
    assertThat(
            (List<Integer>) JsonPath.read(report, "$.snapshot.unidentified[*].ageInDaysAtCutover"))
        .containsExactlyInAnyOrder(503, 58);
    assertThat((List<String>) JsonPath.read(report, "$.snapshot.outstandingItems[*].reference"))
        .containsExactlyInAnyOrder("EFT B-0001", "PAY-778");
    assertThat((List<String>) JsonPath.read(report, "$.snapshot.investments[*].basis"))
        .containsExactlyInAnyOrder("FIRM_DISCRETION", "CLIENT_INSTRUCTION");
    assertThat(
            (List<String>)
                JsonPath.read(
                    report,
                    "$.snapshot.investments[?(@.basis == 'CLIENT_INSTRUCTION')].accruedInterest"))
        .containsExactly("1234.56");
    assertThat((List<String>) JsonPath.read(report, "$.snapshot.flags"))
        .containsExactly("UNIDENTIFIED_OVER_ONE_YEAR");
    assertThat((Boolean) JsonPath.read(report, "$.snapshot.midMonth")).isFalse();
    assertThat((String) JsonPath.read(report, "$.snapshot.totals.eq2.creditors"))
        .isEqualTo("403500.00");
    assertThat((String) JsonPath.read(report, "$.snapshot.signOff.signedBy"))
        .isEqualTo("Report Owner");
    assertThat((String) JsonPath.read(report, "$.snapshot.signOff.preparedBy"))
        .isEqualTo("Report Owner");
    assertThat((Boolean) JsonPath.read(report, "$.snapshot.signOff.samePerson")).isTrue();
    assertThat((List<String>) JsonPath.read(report, "$.snapshot.signOff.notes"))
        .containsExactly("Prepared and signed off by the same person");
    assertThat((String) JsonPath.read(report, "$.snapshot.signOff.statementVersion"))
        .isEqualTo("TAKEON-SIGNOFF-v1");
    assertThat((List<String>) JsonPath.read(report, "$.snapshot.sources[*].section"))
        .containsExactlyInAnyOrder(
            "CLIENT_LEDGER", "UNIDENTIFIED_DEPOSITS", "INVESTMENTS", "OUTSTANDING_ITEMS");
    assertThat((List<String>) JsonPath.read(report, "$.snapshot.sources[*].sha256"))
        .allSatisfy(h -> assertThat(h).hasSize(64));
    assertThat((String) JsonPath.read(report, "$.snapshot.firm.name"))
        .isEqualTo("Report Attorneys");
    assertThat((String) JsonPath.read(report, "$.snapshot.account.name")).isEqualTo(gName);

    // the report.generated audit: ids only
    UUID id = reportId(batch);
    var generated =
        TakeOnTestApi.auditEvents(jdbc, schema, id).stream()
            .filter(e -> e.eventType().equals("takeon.report.generated"))
            .findFirst()
            .orElseThrow();
    assertThat(generated.details())
        .containsOnlyKeys("batch_id", "report_id", "trust_account_id")
        .containsEntry("report_id", id.toString())
        .containsEntry("batch_id", batch.batchId().toString())
        .containsEntry("trust_account_id", acc.g().toString());
  }

  // 1b (FD-25)
  @Test
  void aDualApprovalAccountsReportSaysTheSignOffAuthorisedTheRows() throws Exception {
    Accounts acc = world.freshAccounts(true);
    Validated batch = world.committed(acc);
    String report = reportJson(reportId(batch));
    assertThat((Boolean) JsonPath.read(report, "$.snapshot.account.requireDualApproval")).isTrue();
    assertThat((List<String>) JsonPath.read(report, "$.snapshot.signOff.notes"))
        .containsExactly(
            "Prepared and signed off by the same person", "Authorised by the take-on sign-off");
  }

  // 2
  @Test
  void theReportIsImmutable_andItsHashRecomputesFromTheStoredSnapshot() throws Exception {
    Validated batch = world.committed(world.freshAccounts());
    UUID id = reportId(batch);
    String sha = storedSha(id);

    // Hibernate's own refusal of an update query on an @Immutable entity — nothing else
    assertThatThrownBy(
            () ->
                books.read(
                    () ->
                        entityManager
                            .createQuery(
                                "update TakeOnReport r set r.snapshotSha256 = 'x' where r.id = :id")
                            .setParameter("id", id)
                            .executeUpdate()))
        .satisfies(
            e ->
                assertThat(causeChain(e))
                    .as("an update query on the @Immutable report is refused")
                    .anySatisfy(
                        c ->
                            assertThat(c)
                                .isInstanceOf(org.hibernate.HibernateException.class)
                                .hasMessageContaining(
                                    "The query attempts to update an immutable entity")
                                .hasMessageContaining(TakeOnReport.class.getSimpleName())));
    assertThat(storedSha(id)).isEqualTo(sha);
    assertThat(Arrays.stream(TakeOnReport.class.getMethods()).map(m -> m.getName()))
        .noneMatch(n -> n.startsWith("set"));

    // SHA-256 of the canonical JSON — from the jsonb as Hibernate reads it, and as the API serves
    // it
    Map<String, Object> stored = books.read(() -> reports.findById(id).orElseThrow().getSnapshot());
    assertThat(TakeOnReportSnapshot.sha256(stored)).isEqualTo(sha);
    Map<String, Object> served =
        JSON.readValue(
            JSON.writeValueAsString(JsonPath.read(reportJson(id), "$.snapshot")), Map.class);
    assertThat(TakeOnReportSnapshot.sha256(served)).isEqualTo(sha);
    assertThat((String) JsonPath.read(reportJson(id), "$.snapshotSha256")).isEqualTo(sha);
  }

  // 3
  @Test
  void theReportsNeedViewTrust_andAnUnknownReportIsNotFound() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.committed(acc);
    UUID id = reportId(batch);
    TakeOnTestApi.reports(mockMvc, takeOnOnly(), null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access denied"));
    TakeOnTestApi.report(mockMvc, takeOnOnly(), id)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access denied"));
    TakeOnTestApi.report(mockMvc, world.owner(), UUID.randomUUID())
        .andExpect(status().isNotFound());
    // VIEW_TRUST alone reads both (no MANAGE_TAKE_ON, no FINANCIAL_VISIBILITY)
    TakeOnTestApi.reports(mockMvc, trustReader(), acc.g())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id.toString()));
    TakeOnTestApi.report(mockMvc, trustReader(), id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  // 4
  @Test
  void theListAndTheReportHaveTheirShape() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.committed(acc);
    UUID id = reportId(batch);
    String list =
        json(TakeOnTestApi.reports(mockMvc, world.owner(), acc.g()).andExpect(status().isOk()));
    assertThat((List<Map<String, Object>>) JsonPath.read(list, "$"))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r)
                  .containsOnlyKeys(
                      "id",
                      "batchId",
                      "trustAccountId",
                      "cutoverDate",
                      "generatedAt",
                      "generatedByName",
                      "files");
              assertThat(r.get("id")).isEqualTo(id.toString());
              assertThat(r.get("batchId")).isEqualTo(batch.batchId().toString());
              assertThat(r.get("cutoverDate")).isEqualTo(C);
              assertThat(r.get("generatedByName")).isEqualTo("Report Owner");
            });
    assertThat(
            (List<String>)
                JsonPath.read(json(TakeOnTestApi.reports(mockMvc, world.owner(), null)), "$[*].id"))
        .contains(id.toString());
    Map<String, Object> report = JsonPath.read(reportJson(id), "$");
    assertThat(report)
        .containsOnlyKeys(
            "id",
            "batchId",
            "trustAccountId",
            "cutoverDate",
            "snapshotSha256",
            "generatedAt",
            "snapshot",
            "files");
    assertThat((Map<String, Object>) report.get("snapshot"))
        .containsOnlyKeys(
            "firm",
            "account",
            "cutoverDate",
            "midMonth",
            "previousSystem",
            "kazi",
            "differences",
            "creditorList",
            "investmentAccounts",
            "investments",
            "unidentified",
            "outstandingItems",
            "totals",
            "flags",
            "signOff",
            "sources",
            "batchId");
  }

  // 5 (R-4, D-192)
  @Test
  void anonymisingAClientAfterTheCommitRedactsItsRows_butNeverTheReport() throws Exception {
    Accounts acc = world.freshAccounts();
    var files =
        TakeOnTrustFiles.withRows(
            world.files(acc),
            TakeOnTrustFiles.CLIENT_LEDGER,
            List.of("A,A1,300000.00,", "B,B1,150000.00,", "Z,Z1,49500.00,"));
    var batch = world.validated(acc.g(), files, TakeOnTrustFiles.section17313Position(C));
    world.signOff(batch);
    world.claim(batch);
    world.drain();
    UUID id = reportId(batch);
    String before = reportJson(id);
    String sha = storedSha(id);

    mockMvc
        .perform(
            post("/api/clients/" + world.client("Z") + "/anonymize")
                .with(world.owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationName\": \"Client Z tor\", \"reason\": \"test\"}"))
        .andExpect(status().isOk());

    var zRow =
        jdbc.queryForMap(
            ("SELECT raw_values::text AS raw, mapped_values::text AS mapped, status"
                    + " FROM \"%s\".take_on_rows WHERE batch_id = ? AND section = 'CLIENT_LEDGER'"
                    + " AND row_number = 4")
                .formatted(schema),
            batch.batchId());
    assertThat(zRow)
        .containsEntry("raw", "{\"redacted\": true}")
        .containsEntry("mapped", "{\"redacted\": true}")
        .containsEntry("status", "CREATED");
    assertThat(storedSha(id)).isEqualTo(sha);
    assertThat(reportJson(id)).isEqualTo(before);
  }

  // --- 657D: the report files (task 657.31) ---

  // 6
  @Test
  void theDrainRendersBothFiles_andTheDownloadsAreTheStoredBytes_audited() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.committed(acc);
    UUID id = reportId(batch);

    var rows = fileRows(id);
    assertThat(rows).extracting(r -> r.get("format")).containsExactlyInAnyOrder("PDF", "CSV");
    for (var row : rows) {
      String ext = ((String) row.get("format")).toLowerCase(java.util.Locale.ROOT);
      assertThat((String) row.get("s3_key"))
          .isEqualTo(
              "take-on/%s/%s/report/%s/take-on-report.%s"
                  .formatted(schema, batch.batchId(), id, ext));
      assertThat((String) row.get("sha256")).matches("[0-9a-f]{64}");
      assertThat(((Number) row.get("file_size")).longValue()).isPositive();
      assertThat(sha256(storage.download((String) row.get("s3_key")))).isEqualTo(row.get("sha256"));
    }
    String report = reportJson(id);
    assertThat((String) JsonPath.read(report, "$.files.pdf.status")).isEqualTo("READY");
    assertThat((String) JsonPath.read(report, "$.files.csv.status")).isEqualTo("READY");
    assertThat((String) JsonPath.read(report, "$.files.pdf.sha256")).isEqualTo(shaOf(id, "PDF"));

    var pdf =
        TakeOnTestApi.reportFile(mockMvc, world.owner(), id, "pdf")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(pdf.getContentType()).isEqualTo("application/pdf");
    assertThat(pdf.getHeader("Content-Disposition")).startsWith("attachment;");
    assertThat(pdf.getHeader("Content-Disposition")).contains(".pdf");
    assertThat(sha256(pdf.getContentAsByteArray())).isEqualTo(shaOf(id, "PDF"));

    var csv =
        TakeOnTestApi.reportFile(mockMvc, world.owner(), id, "csv")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(csv.getContentType()).startsWith("text/csv");
    assertThat(sha256(csv.getContentAsByteArray())).isEqualTo(shaOf(id, "CSV"));
    var csvLines = new String(csv.getContentAsByteArray(), StandardCharsets.UTF_8).lines().toList();
    assertThat(csvLines.get(0)).startsWith("# Trust take-on report — ");
    assertThat(csvLines.get(0)).endsWith(" at 31 October 2026");
    assertThat(csvLines.get(2)).isEqualTo("Section,Group,Client,Matter,Reference,Date,Amount");
    assertThat(csvLines.get(3)).startsWith("CREDITOR,Client ledgers,");
    assertThat(csvLines).anyMatch(l -> l.startsWith("CREDITOR,Fund interest and charges ledger,"));
    assertThat(csvLines).anyMatch(l -> l.startsWith("OUTSTANDING,Unpresented payment,"));

    // each download audited: ids and the format only
    var downloads =
        TakeOnTestApi.auditEvents(jdbc, schema, id).stream()
            .filter(e -> e.eventType().equals("takeon.report.downloaded"))
            .toList();
    assertThat(downloads).hasSize(2);
    assertThat(downloads)
        .allSatisfy(
            e ->
                assertThat(e.details())
                    .containsOnlyKeys("report_id", "format")
                    .containsEntry("report_id", id.toString()));
    assertThat(downloads).extracting(e -> e.details().get("format")).containsExactly("PDF", "CSV");

    // byte-stable: the same report renders to the same bytes, = the stored SHA-256 (A-17)
    var stored = books.read(() -> reports.findById(id).orElseThrow());
    byte[] first = renderer.renderPdf(stored);
    byte[] second = renderer.renderPdf(stored);
    assertThat(second).isEqualTo(first);
    assertThat(sha256(first)).isEqualTo(shaOf(id, "PDF"));
    assertThat(sha256(renderer.renderCsv(stored))).isEqualTo(shaOf(id, "CSV"));
    try (var doc = org.apache.pdfbox.Loader.loadPDF(first)) {
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
      assertThat(text)
          .contains(
              "Trust take-on report",
              "Reconciled position at cutover",
              "Previous system",
              "Difference",
              "Trust creditors at cutover",
              "Client ledgers",
              "Fund interest and charges ledger",
              "Unidentified deposits",
              "Section 86(4) investments",
              "Section 86(3) investments",
              "Outstanding items at cutover",
              "Control totals",
              "Flags",
              "Sign-off",
              "Source files",
              "Legal Practitioners' Fidelity Fund",
              "31 October 2026");
      assertThat(text).doesNotContain("CLIENT_INSTRUCTION", "UNPRESENTED_PAYMENT", "TAKEON_");
    }
  }

  // 7
  @Test
  void aLostFile_isRenderedAgainFromTheSnapshot_andTheOtherFileIsNeverOverwritten()
      throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    world.signOff(batch);
    world.claim(batch);
    TakeOnTestDrain.run(drainWith(failingStorage(".csv")), schema, ORG_ID);
    UUID id = reportId(batch);

    assertThat(fileRows(id)).extracting(r -> r.get("format")).containsExactly("PDF");
    String report = reportJson(id);
    assertThat((String) JsonPath.read(report, "$.files.pdf.status")).isEqualTo("READY");
    assertThat((String) JsonPath.read(report, "$.files.csv.status")).isEqualTo("RENDERING");
    TakeOnTestApi.reportFile(mockMvc, world.owner(), id, "csv")
        .andExpect(status().isAccepted())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Retry-After", "5"))
        .andExpect(jsonPath("$.status").value(202))
        .andExpect(jsonPath("$.code").value("REPORT_RENDERING"))
        .andExpect(jsonPath("$.title").value("Take-on report files being prepared"))
        .andExpect(jsonPath("$.detail").value("Preparing the report files…"));
    assertThat(sweep.tenantsWithWork(List.of(mine())))
        .extracting(OrgSchemaMapping::getSchemaName)
        .containsExactly(schema);
    var pdfBefore = fileRow(id, "PDF");

    world.drain();
    assertThat(fileRows(id))
        .extracting(r -> r.get("format"))
        .containsExactlyInAnyOrder("PDF", "CSV");
    assertThat(fileRow(id, "PDF")).isEqualTo(pdfBefore);
    assertThat((String) JsonPath.read(reportJson(id), "$.files.csv.status")).isEqualTo("READY");
  }

  // 8
  @Test
  void theFifthFailure_givesTheRenderUp_theSweepSkipsIt_andTryAgainRendersIt() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    world.signOff(batch);
    world.claim(batch);
    var broken = failingStorage(".");
    TakeOnTestDrain.run(drainWith(broken), schema, ORG_ID); // attempt 1
    UUID id = reportId(batch);
    var failing = rendererWith(broken);
    for (int attempt = 2; attempt <= 5; attempt++) {
      RequestScopesRun.inTenant(schema, ORG_ID, () -> failing.renderOne(id));
    }
    var render = renderRow(id);
    assertThat(((Number) render.get("attempts")).intValue()).isEqualTo(5);
    assertThat(render.get("failed_at")).isNotNull();
    assertThat((String) render.get("last_error_code")).isEqualTo("IllegalStateException");
    assertThat(fileRows(id)).isEmpty();
    String report = reportJson(id);
    assertThat((String) JsonPath.read(report, "$.files.pdf.status")).isEqualTo("FAILED");
    assertThat((String) JsonPath.read(report, "$.files.csv.status")).isEqualTo("FAILED");
    TakeOnTestApi.reportFile(mockMvc, world.owner(), id, "pdf")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("REPORT_RENDER_FAILED"))
        .andExpect(jsonPath("$.title").value("Take-on report files not produced"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "The report files could not be produced. The report itself is saved and"
                        + " unchanged. Try again, or contact support if it fails again."));

    // the real drain leaves it alone (and renders any other report of the tenant); the sweep
    // no longer finds work
    world.drain();
    assertThat(fileRows(id)).isEmpty();
    assertThat(sweep.tenantsWithWork(List.of(mine()))).isEmpty();

    // "Try again": a fresh cycle — RENDERING, the sweep finds it, the drain renders it
    String retried =
        json(TakeOnTestApi.renderReport(mockMvc, world.owner(), id).andExpect(status().isOk()));
    assertThat((String) JsonPath.read(retried, "$.files.pdf.status")).isEqualTo("RENDERING");
    assertThat((String) JsonPath.read(retried, "$.files.csv.status")).isEqualTo("RENDERING");
    var reset = renderRow(id);
    assertThat(((Number) reset.get("attempts")).intValue()).isZero();
    assertThat(reset.get("failed_at")).isNull();
    assertThat(sweep.tenantsWithWork(List.of(mine())))
        .extracting(OrgSchemaMapping::getSchemaName)
        .containsExactly(schema);
    world.drain();
    String ready = reportJson(id);
    assertThat((String) JsonPath.read(ready, "$.files.pdf.status")).isEqualTo("READY");
    assertThat((String) JsonPath.read(ready, "$.files.csv.status")).isEqualTo("READY");
    // a second "Try again" on a ready report changes nothing
    TakeOnTestApi.renderReport(mockMvc, world.owner(), id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.files.pdf.status").value("READY"));
  }

  // 9
  @Test
  void theFileEndpointsNeedViewTrust_andAnUnknownReportIsNotFound() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.committed(acc);
    UUID id = reportId(batch);
    for (String format : List.of("pdf", "csv")) {
      TakeOnTestApi.reportFile(mockMvc, takeOnOnly(), id, format)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.title").value("Access denied"))
          .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
      TakeOnTestApi.reportFile(mockMvc, trustReader(), id, format).andExpect(status().isOk());
    }
    TakeOnTestApi.renderReport(mockMvc, takeOnOnly(), id)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
    TakeOnTestApi.renderReport(mockMvc, trustReader(), id).andExpect(status().isOk());
    UUID unknown = UUID.randomUUID();
    TakeOnTestApi.reportFile(mockMvc, world.owner(), unknown, "pdf")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Take-on report not found"))
        .andExpect(jsonPath("$.detail").value("No take-on report found with id " + unknown));
    TakeOnTestApi.renderReport(mockMvc, world.owner(), unknown)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Take-on report not found"))
        .andExpect(jsonPath("$.detail").value("No take-on report found with id " + unknown));
  }

  // --- 657D helpers ---

  /** A drain built by hand whose renderer stores through {@code storage} (no new context key). */
  private TakeOnDrainJobHandler drainWith(StorageService storage) {
    return new TakeOnDrainJobHandler(
        batchRepository, rowRunner, committers, rendererWith(storage), transactionManager, clock);
  }

  private TakeOnReportRenderer rendererWith(StorageService storage) {
    return new TakeOnReportRenderer(
        reports,
        reportFiles,
        reportRenders,
        storage,
        templateEngine,
        pdfRenderingService,
        transactionManager,
        clock);
  }

  /** The test storage, refusing every upload whose key contains {@code refused}. */
  private StorageService failingStorage(String refused) {
    return new StorageService() {
      @Override
      public String upload(String key, byte[] content, String contentType) {
        if (key.contains(refused)) {
          throw new IllegalStateException("storage unavailable");
        }
        return storage.upload(key, content, contentType);
      }

      @Override
      public String upload(String key, InputStream content, long length, String contentType) {
        throw new IllegalStateException("storage unavailable");
      }

      @Override
      public byte[] download(String key) {
        return storage.download(key);
      }

      @Override
      public void delete(String key) {
        storage.delete(key);
      }

      @Override
      public PresignedUrl generateUploadUrl(String key, String contentType, Duration expiry) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PresignedUrl generateDownloadUrl(String key, Duration expiry) {
        throw new UnsupportedOperationException();
      }

      @Override
      public List<String> listKeys(String prefix) {
        return storage.listKeys(prefix);
      }
    };
  }

  private OrgSchemaMapping mine() {
    return mappings.findBySchemaName(schema).orElseThrow();
  }

  private List<Map<String, Object>> fileRows(UUID reportId) {
    return jdbc.queryForList(
        "SELECT id, format, s3_key, sha256, file_size, rendered_at FROM \"%s\".take_on_report_files WHERE report_id = ? ORDER BY format"
            .formatted(schema),
        reportId);
  }

  private Map<String, Object> fileRow(UUID reportId, String format) {
    return fileRows(reportId).stream()
        .filter(r -> format.equals(r.get("format")))
        .findFirst()
        .orElseThrow();
  }

  private String shaOf(UUID reportId, String format) {
    return (String) fileRow(reportId, format).get("sha256");
  }

  private Map<String, Object> renderRow(UUID reportId) {
    return jdbc.queryForMap(
        "SELECT attempts, last_error_code, failed_at FROM \"%s\".take_on_report_renders WHERE report_id = ?"
            .formatted(schema),
        reportId);
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  /** Runs {@code action} bound to the tenant (as the drain's job worker binds it). */
  private static final class RequestScopesRun {
    static void inTenant(String schema, String orgId, Runnable action) {
      io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.runForTenant(schema, orgId, action);
    }
  }

  // --- helpers ---

  private UUID reportId(Validated batch) throws Exception {
    return UUID.fromString(JsonPath.read(world.batchJson(batch.batchId()), "$.reportId"));
  }

  private String reportJson(UUID id) throws Exception {
    return json(TakeOnTestApi.report(mockMvc, world.owner(), id).andExpect(status().isOk()));
  }

  private String storedSha(UUID id) {
    return jdbc.queryForObject(
        "SELECT snapshot_sha256 FROM \"%s\".take_on_reports WHERE id = ?".formatted(schema),
        String.class,
        id);
  }

  private String accountName(UUID account) {
    return jdbc.queryForObject(
        "SELECT account_name FROM \"%s\".trust_accounts WHERE id = ?".formatted(schema),
        String.class,
        account);
  }

  private String createRole(String name, String... capabilities) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(world.owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":%s}"""
                            .formatted(name, JSON.writeValueAsString(List.of(capabilities)))))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(UUID memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(world.owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private RequestPostProcessor trustReader() {
    return TestJwtFactory.customRoleJwt(ORG_ID, TRUST_READER);
  }

  private RequestPostProcessor takeOnOnly() {
    return TestJwtFactory.customRoleJwt(ORG_ID, TAKE_ON_ONLY);
  }

  /** {@code e} and its causes, outermost first. */
  private static List<Throwable> causeChain(Throwable e) {
    var chain = new java.util.ArrayList<Throwable>();
    for (Throwable c = e; c != null && !chain.contains(c); c = c.getCause()) {
      chain.add(c);
    }
    return chain;
  }
}
