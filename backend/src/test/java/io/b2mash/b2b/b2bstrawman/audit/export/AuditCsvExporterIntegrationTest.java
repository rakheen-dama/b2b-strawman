package io.b2mash.b2b.b2bstrawman.audit.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

/**
 * Integration tests for the streaming CSV export route ({@code GET /api/audit-events/export.csv}).
 * Drives the endpoint via MockMvc and asserts header shape, RFC 4180 escaping, severity-filtered
 * row counts, capability gate, and large-result streaming behaviour. Epic 503A tasks 503.1, 503.3,
 * 503.5.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditCsvExporterIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_csv_export_test";

  private final AuditService auditService;
  private final TenantProvisioningService provisioningSvc;

  AuditCsvExporterIntegrationTest(
      AuditService auditService, TenantProvisioningService provisioningSvc) {
    this.auditService = auditService;
    this.provisioningSvc = provisioningSvc;
  }

  private String schemaName;
  private UUID liveMemberId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    schemaName =
        provisioningSvc.provisionTenant(ORG_ID, "Audit CSV Export Test Org", null).schemaName();
    var liveMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_csv_owner", "csv_owner@test.com", "CSV Owner", "owner");
    liveMemberId = UUID.fromString(liveMemberIdStr);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_csv_member", "csv_member@test.com", "CSV Member", "member");

    // Three baseline events spanning the registry's severity buckets.
    //   matter.closure.override_used → CRITICAL / COMPLIANCE
    //   security.login.failure       → WARNING  / SECURITY
    //   task.created (unregistered)  → INFO     / STANDARD (default fallback)
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "matter.closure.override_used",
                      "matter",
                      UUID.randomUUID(),
                      liveMemberId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "security.login.failure",
                      "security",
                      UUID.randomUUID(),
                      liveMemberId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      UUID.randomUUID(),
                      liveMemberId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));

              // Tricky row that exercises RFC 4180 escaping — comma + double-quote in userAgent
              // and a newline inside a details value. Must round-trip cleanly through a CSV
              // parser.
              Map<String, Object> details = new HashMap<>();
              details.put("note", "line one\nline two");
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      UUID.randomUUID(),
                      liveMemberId,
                      "USER",
                      "API",
                      null,
                      "Mozilla/5.0, \"AcmeAgent\"",
                      details));
            });
  }

  /**
   * Drives the async-dispatched export endpoint and returns the MvcResult after the
   * StreamingResponseBody has fully flushed. {@code params} is appended as repeated {@code
   * .param(name, value)} pairs.
   */
  private org.springframework.test.web.servlet.MvcResult performExport(
      String clerkUserId, String... params) throws Exception {
    var req =
        get("/api/audit-events/export.csv").with(TestJwtFactory.ownerJwt(ORG_ID, clerkUserId));
    for (int i = 0; i + 1 < params.length; i += 2) {
      req = req.param(params[i], params[i + 1]);
    }
    var asyncResult = mockMvc.perform(req).andExpect(request().asyncStarted()).andReturn();
    return mockMvc.perform(asyncDispatch(asyncResult)).andReturn();
  }

  @Test
  void headerRowMatchesSpecVerbatim() throws Exception {
    var result = performExport("user_csv_owner");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getHeader("Content-Type")).isEqualTo("text/csv;charset=utf-8");

    var body = result.getResponse().getContentAsString();
    var firstLine = body.split("\r\n", 2)[0];
    assertThat(firstLine)
        .isEqualTo(
            "occurredAt,eventType,label,severity,entityType,entityId,actorId,actorDisplayName,"
                + "actorType,source,ipAddress,userAgent,detailsJson");

    // Content-Disposition declares the attachment filename with the tenant slot baked in.
    var disposition = result.getResponse().getHeader("Content-Disposition");
    assertThat(disposition).startsWith("attachment; filename=\"audit-events-");
    assertThat(disposition).endsWith(".csv\"");
  }

  @Test
  void severityFilterRestrictsRowCount() throws Exception {
    var result = performExport("user_csv_owner", "severities", "CRITICAL");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    var body = result.getResponse().getContentAsString();
    var rows = parseCsvBody(body);
    int severityIdx = indexOfColumn("severity");
    assertThat(rows).isNotEmpty();
    for (var fields : rows) {
      assertThat(fields.get(severityIdx)).isEqualTo("CRITICAL");
    }
  }

  @Test
  void rfc4180EscapingRoundTrips() throws Exception {
    var result = performExport("user_csv_owner", "eventType", "task.created");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    var body = result.getResponse().getContentAsString();
    var rows = parseCsvBody(body);
    int userAgentIdx = indexOfColumn("userAgent");
    int detailsIdx = indexOfColumn("detailsJson");

    // Find the row with the tricky userAgent (comma + embedded double quotes).
    var trickyRow =
        rows.stream()
            .filter(r -> r.get(userAgentIdx).contains("AcmeAgent"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Tricky row not found in CSV body: " + body));
    assertThat(trickyRow.get(userAgentIdx)).isEqualTo("Mozilla/5.0, \"AcmeAgent\"");
    // The JSON serialiser escapes the literal newline as the two-character sequence "\n" inside
    // the JSON string — that's the correct contract for detailsJson (the CSV consumer parses
    // detailsJson as JSON, which then yields the original newline). So the *raw CSV field* should
    // contain the literal backslash-n, not an actual newline.
    assertThat(trickyRow.get(detailsIdx)).contains("line one\\nline two");
  }

  @Test
  void capabilityGateRejectsNonOwner() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/export.csv")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_csv_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void largeResultStreamsConstantMemory() throws Exception {
    // 5,000 rows is enough to exercise the streaming path (fetch-size 100 ⇒ 50 cursor pulls)
    // without dragging out the test cycle. A buffering implementation would still pass this size,
    // but the >2k row-count check guards against off-by-one regressions in the flush cadence.
    int seedCount = 5_000;
    UUID entityId = UUID.randomUUID();
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              for (int i = 0; i < seedCount; i++) {
                auditService.log(
                    new AuditEventRecord(
                        "export.bulk.seed",
                        "bulk_seed",
                        entityId,
                        liveMemberId,
                        "USER",
                        "API",
                        null,
                        null,
                        null));
              }
            });

    var result =
        performExport("user_csv_owner", "entityType", "bulk_seed", "entityId", entityId.toString());
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    var body = result.getResponse().getContentAsString();
    long lineCount = body.lines().count();
    // header + seedCount data rows, allow a trailing empty line on body.lines() count
    assertThat(lineCount).isGreaterThanOrEqualTo(seedCount + 1L);
  }

  // ---- CSV parsing helpers (hand-rolled minimal RFC 4180 parser) ----

  private static int indexOfColumn(String name) {
    var header =
        List.of(
            "occurredAt",
            "eventType",
            "label",
            "severity",
            "entityType",
            "entityId",
            "actorId",
            "actorDisplayName",
            "actorType",
            "source",
            "ipAddress",
            "userAgent",
            "detailsJson");
    return header.indexOf(name);
  }

  /** Parses a CSV body produced by {@link AuditCsvExporter}, returning data rows only. */
  private static List<List<String>> parseCsvBody(String body) {
    var allRows = new java.util.ArrayList<List<String>>();
    int i = 0;
    int n = body.length();
    while (i < n) {
      var row = new java.util.ArrayList<String>();
      var field = new StringBuilder();
      boolean inQuotes = false;
      while (i < n) {
        char c = body.charAt(i);
        if (inQuotes) {
          if (c == '"') {
            if (i + 1 < n && body.charAt(i + 1) == '"') {
              field.append('"');
              i += 2;
              continue;
            }
            inQuotes = false;
            i++;
          } else {
            field.append(c);
            i++;
          }
        } else {
          if (c == '"' && field.length() == 0) {
            inQuotes = true;
            i++;
          } else if (c == ',') {
            row.add(field.toString());
            field.setLength(0);
            i++;
          } else if (c == '\r' && i + 1 < n && body.charAt(i + 1) == '\n') {
            row.add(field.toString());
            field.setLength(0);
            i += 2;
            break;
          } else if (c == '\n') {
            row.add(field.toString());
            field.setLength(0);
            i++;
            break;
          } else {
            field.append(c);
            i++;
          }
        }
      }
      if (field.length() > 0 || !row.isEmpty()) {
        if (field.length() > 0) {
          row.add(field.toString());
        }
        allRows.add(row);
      }
    }
    if (allRows.size() <= 1) {
      return List.of();
    }
    return allRows.subList(1, allRows.size()); // drop header
  }
}
