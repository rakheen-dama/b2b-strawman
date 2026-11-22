package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Phase 92 FU-12, CodeRabbit round 1 (connection budget): how many pooled connections are checked
 * out of the tenant pool ({@code appDataSource}, the primary shard) while a report's audit row is
 * written, on the real HTTP path.
 *
 * <p>Sampled with {@code HikariPoolMXBean.getActiveConnections()} at three points of one PDF
 * export: inside the report transaction (in {@code renderPdf}); in an {@code afterCommit} callback
 * the test registers on the report transaction (a probe of the framework only: production code
 * registers no synchronization); and inside the audit write ({@code AuditService.log}, after the
 * real call, so the audit transaction's own connection has been acquired by its member lookup).
 * Scheduling is off in tests, so the pool is otherwise idle; the idle count is sampled first and
 * every assertion is relative to it.
 *
 * <p>Measured before the fix (the row written from {@code afterCommit} in {@code REQUIRES_NEW}):
 * idle 0, inside the report transaction 1, at {@code afterCommit} 1 (the report transaction's
 * connection is still checked out there), during the audit write 2. Now the entry point returns its
 * row and {@link ReportRunService} writes it after the entry point has returned: the audit write
 * holds 1, and these tests guard it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportAuditConnectionBudgetIntegrationTest {

  private static final String ORG_ID = "org_report_audit_conn_budget";
  private static final String OWNER = "user_racb_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @Autowired
  @Qualifier("appDataSource")
  private HikariDataSource appDataSource;

  @MockitoSpyBean private ReportRenderingService reportRenderingService;
  @MockitoSpyBean private AuditService auditService;

  /** Active connections by sample point, for the current test. */
  private final Map<String, Integer> samples = new ConcurrentHashMap<>();

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Report Audit Connection Budget Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "racb_owner@test.com", "RACB Owner", "owner");
  }

  @BeforeEach
  void installProbes() {
    samples.clear();
    doAnswer(
            inv -> {
              Object pdf = inv.callRealMethod();
              samples.put("insideReportTransaction", active());
              samples.put(
                  "reportTransactionReadOnly",
                  TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? 1 : 0);
              TransactionSynchronizationManager.registerSynchronization(
                  new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                      samples.put("afterCommitBeforeAuditWrite", active());
                    }
                  });
              return pdf;
            })
        .when(reportRenderingService)
        .renderPdf(any(), any(), any());
    doAnswer(
            inv -> {
              inv.callRealMethod();
              AuditEventRecord record = inv.getArgument(0);
              if (record.eventType().startsWith("REPORT_")) {
                samples.put("duringAuditWrite", active());
              }
              return null;
            })
        .when(auditService)
        .log(any());
  }

  @Test
  void pdfExport_auditWriteHoldsAtMostOneConnection() throws Exception {
    int idle = active();

    mockMvc
        .perform(
            get("/api/report-definitions/timesheet/export/pdf")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .param("dateFrom", "2025-06-01")
                .param("dateTo", "2025-06-30")
                .param("groupBy", "member"))
        .andExpect(status().isOk());

    measured("PDF export", idle);
    assertThat(samples.get("reportTransactionReadOnly"))
        .as("report tx read-only; idle=%d samples=%s", idle, samples)
        .isEqualTo(1);
    assertThat(samples.get("insideReportTransaction")).isEqualTo(idle + 1);
    assertThat(samples.get("duringAuditWrite"))
        .as(
            "connections checked out while the audit row is written; idle=%d samples=%s",
            idle, samples)
        .isEqualTo(idle + 1);
  }

  /**
   * The HTTP path (through {@link ReportRunService}): a PDF export whose transaction rolls back
   * after its audit row was built (the PDF rendered) writes no row.
   */
  @Test
  void pdfExportFailingAfterTheAuditWasBuilt_writesNoRow() throws Exception {
    doThrow(
            new io.b2mash.b2b.b2bstrawman.exception.InvalidStateException(
                "Probe failure", "failed after the audit row was built"))
        .when(reportRenderingService)
        .generateFilename(any(), any(), any());

    mockMvc
        .perform(
            get("/api/report-definitions/timesheet/export/pdf")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .param("dateFrom", "2025-06-01")
                .param("dateTo", "2025-06-30")
                .param("groupBy", "member"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Probe failure"))
        .andExpect(jsonPath("$.detail").value("failed after the audit row was built"));

    assertThat(samples).as("the PDF was rendered").containsKey("insideReportTransaction");
    verify(auditService, never())
        .log(argThat(r -> r != null && r.eventType().startsWith("REPORT_")));
  }

  @Test
  void execute_auditWriteHoldsAtMostOneConnection() throws Exception {
    int idle = active();

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

    measured("execute", idle);
    assertThat(samples.get("duringAuditWrite"))
        .as(
            "connections checked out while the audit row is written; idle=%d samples=%s",
            idle, samples)
        .isEqualTo(idle + 1);
  }

  @Test
  void csvExport_auditWriteHoldsAtMostOneConnection() throws Exception {
    int idle = active();

    mockMvc
        .perform(
            get("/api/report-definitions/timesheet/export/csv")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .param("dateFrom", "2025-06-01")
                .param("dateTo", "2025-06-30")
                .param("groupBy", "member"))
        .andExpect(status().isOk());

    measured("CSV export", idle);
    assertThat(samples.get("duringAuditWrite"))
        .as(
            "connections checked out while the audit row is written; idle=%d samples=%s",
            idle, samples)
        .isEqualTo(idle + 1);
  }

  /** Appends the samples to {@code target/fu12-connection-budget.txt} (evidence, FU-12 notes). */
  private void measured(String path, int idle) throws java.io.IOException {
    var target = java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
    java.nio.file.Files.writeString(
        target.resolve("fu12-connection-budget.txt"),
        "%s %s: idle=%d samples=%s%n".formatted(java.time.Instant.now(), path, idle, samples),
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND);
  }

  private int active() {
    return appDataSource.getHikariPoolMXBean().getActiveConnections();
  }
}
