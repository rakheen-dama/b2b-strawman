package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 FU-12 (650C F-650C-1): every report run and export leaves exactly one audit row, written
 * for the caller in the caller's tenant. Before the fix the {@code REPORT_GENERATED} (execute) and
 * the PDF {@code REPORT_EXPORTED} rows joined the report's read-only transaction (Hibernate flush
 * mode MANUAL) and were never written; the CSV row, written outside the transaction, was.
 *
 * <p>Rows are id-scoped (the ids a call adds), never a global count. One legal-za tenant, so the
 * Section 35 pack runs on the real trust report pack.
 *
 * <p>The report transaction itself stays read-only REPEATABLE_READ (FU-3 D-1): guarded by {@code
 * ReportExecutionServiceTest.reportEntryPoints_readOneSnapshot_readOnlyRepeatableRead} and,
 * behaviourally, by the report tests in {@code TrustSnapshotReadsIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportAuditRowsIntegrationTest {

  private static final String ORG_ID = "org_report_audit_rows";
  private static final String OWNER = "user_rar_owner";
  private static final String OWNER_NAME = "RAR Owner";
  private static final String TIMESHEET = "timesheet";
  private static final String SECTION_35 = "section-35-data-pack";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ReportDefinitionRepository reportDefinitionRepository;
  @Autowired private ReportExportService reportExportService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;
  private UUID ownerId;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Report Audit Rows Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "rar_owner@test.com", OWNER_NAME, "owner"));
    fixture.enableTrustModule(schema);
    trustAccountId = fixture.tenant(schema, ORG_ID, ownerId).freshAccount(false);
  }

  // --- one row per run / export, for the caller, in the tenant -----------------------------

  @Test
  void execute_writesOneReportGeneratedRow() throws Exception {
    var before = rowIds("REPORT_GENERATED", TIMESHEET);

    mockMvc
        .perform(
            post("/api/report-definitions/timesheet/execute")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"parameters": {"dateFrom": "2025-06-01", "dateTo": "2025-06-30",
                     "groupBy": "member"}, "page": 0, "size": 50}
                    """))
        .andExpect(status().isOk());

    assertOneNewRowByOwner("REPORT_GENERATED", TIMESHEET, before, "preview");
  }

  @Test
  void pdfExport_writesOneReportExportedRow() throws Exception {
    var before = rowIds("REPORT_EXPORTED", TIMESHEET);

    mockMvc
        .perform(
            get("/api/report-definitions/timesheet/export/pdf")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .param("dateFrom", "2025-06-01")
                .param("dateTo", "2025-06-30")
                .param("groupBy", "member"))
        .andExpect(status().isOk());

    assertOneNewRowByOwner("REPORT_EXPORTED", TIMESHEET, before, "pdf");
  }

  /** The register's headline case: the Section 35 pack PDF left no audit trail. */
  @Test
  void section35PdfExport_writesOneReportExportedRow() throws Exception {
    var before = rowIds("REPORT_EXPORTED", SECTION_35);

    mockMvc
        .perform(
            get("/api/report-definitions/section-35-data-pack/export/pdf")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .param("trust_account_id", trustAccountId.toString())
                .param("financial_year_end", "2026-03-31"))
        .andExpect(status().isOk());

    var row = assertOneNewRowByOwner("REPORT_EXPORTED", SECTION_35, before, "pdf");
    assertThat(row.getDetails()).containsEntry("slug", SECTION_35);
  }

  @Test
  void csvExport_writesOneReportExportedRow() throws Exception {
    var before = rowIds("REPORT_EXPORTED", TIMESHEET);

    mockMvc
        .perform(
            get("/api/report-definitions/timesheet/export/csv")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .param("dateFrom", "2025-06-01")
                .param("dateTo", "2025-06-30")
                .param("groupBy", "member"))
        .andExpect(status().isOk());

    assertOneNewRowByOwner("REPORT_EXPORTED", TIMESHEET, before, "csv");
  }

  /** A refused export (a required parameter missing) is not audited as exported. */
  @Test
  void refusedPdfExport_writesNoRow() throws Exception {
    var before = rowIds("REPORT_EXPORTED", SECTION_35);

    mockMvc
        .perform(
            get("/api/report-definitions/section-35-data-pack/export/pdf")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .param("financial_year_end", "2026-03-31"))
        .andExpect(status().is4xxClientError());

    assertThat(newRows("REPORT_EXPORTED", SECTION_35, before)).isEmpty();
  }

  // --- the row is written after the report transaction commits -----------------------------

  /**
   * An export whose transaction rolls back is not audited as exported; the same export committed
   * is, once. (The report entry point joins the test's transaction here, so the rollback is the
   * export's own.)
   */
  @Test
  void exportInATransactionThatRollsBack_writesNoRow_committed_writesOne() {
    Map<String, Object> params =
        Map.of("dateFrom", "2025-06-01", "dateTo", "2025-06-30", "groupBy", "member");

    var beforeRollback = rowIds("REPORT_EXPORTED", TIMESHEET);
    asOwner(
        () ->
            transactionTemplate.execute(
                tx -> {
                  reportExportService.exportAsPdf(TIMESHEET, params);
                  tx.setRollbackOnly();
                  return null;
                }));
    assertThat(newRows("REPORT_EXPORTED", TIMESHEET, beforeRollback))
        .as("rolled-back export")
        .isEmpty();

    var beforeCommit = rowIds("REPORT_EXPORTED", TIMESHEET);
    asOwner(
        () ->
            transactionTemplate.execute(tx -> reportExportService.exportAsPdf(TIMESHEET, params)));
    assertThat(newRows("REPORT_EXPORTED", TIMESHEET, beforeCommit))
        .as("committed export")
        .hasSize(1);
  }

  // --- helpers -------------------------------------------------------------------------------

  /** Exactly one row added since {@code before}: the owner's, enriched, in this tenant. */
  private AuditEvent assertOneNewRowByOwner(
      String eventType, String slug, Set<UUID> before, String format) {
    var added = newRows(eventType, slug, before);
    assertThat(added).as("%s rows added for %s", eventType, slug).hasSize(1);
    var row = added.getFirst();
    assertThat(row.getEntityType()).isEqualTo("REPORT");
    assertThat(row.getEntityId()).isEqualTo(definitionId(slug));
    assertThat(row.getActorId()).isEqualTo(ownerId);
    assertThat(row.getActorType()).isEqualTo("USER");
    // Resolved from this tenant's members table, so the write ran against the tenant's schema
    assertThat(row.getDetails())
        .containsEntry("actor_name", OWNER_NAME)
        .containsEntry("format", format);
    return row;
  }

  private Set<UUID> rowIds(String eventType, String slug) {
    return rows(eventType, slug).stream().map(AuditEvent::getId).collect(Collectors.toSet());
  }

  private List<AuditEvent> newRows(String eventType, String slug, Set<UUID> before) {
    return rows(eventType, slug).stream().filter(e -> !before.contains(e.getId())).toList();
  }

  /** The tenant's rows of {@code eventType} for one report definition. */
  private List<AuditEvent> rows(String eventType, String slug) {
    var definition = definitionId(slug);
    return inTenant(
        () ->
            auditEventRepository
                .findByFilter(
                    "REPORT", definition, null, eventType, null, null, PageRequest.of(0, 500))
                .getContent()
                .stream()
                .filter(e -> e.getEventType().equals(eventType))
                .toList());
  }

  private UUID definitionId(String slug) {
    return inTenant(() -> reportDefinitionRepository.findBySlug(slug).orElseThrow().getId());
  }

  private <T> T inTenant(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }

  /** As the owner, with no transaction of the test's own. */
  private <T> T asOwner(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("FINANCIAL_VISIBILITY"))
        .call(body::get);
  }
}
