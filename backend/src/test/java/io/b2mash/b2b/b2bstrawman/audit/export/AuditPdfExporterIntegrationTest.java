package io.b2mash.b2b.b2bstrawman.audit.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the PDF audit-export route ({@code GET /api/audit-events/export.pdf}, Epic
 * 504A). Exercises the Tiptap → openhtmltopdf pipeline end-to-end via MockMvc, asserting:
 *
 * <ul>
 *   <li>the response is a non-empty {@code application/pdf} attachment with a {@code %PDF-} magic
 *       header
 *   <li>the reflexive {@code audit.export.generated} event is emitted with {@code format=PDF},
 *       {@code rowCount}, and the effective filter
 *   <li>the 10,000-row pre-flight cap returns a 413 {@link org.springframework.http.ProblemDetail}
 *       carrying {@code rowCount} and {@code cap} (no PDF body, no reflexive event)
 *   <li>the {@code TEAM_OVERSIGHT} capability gate rejects non-owners with a 403
 * </ul>
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditPdfExporterIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_pdf_export_test";
  private static final String OTHER_ORG_ID = "org_audit_pdf_export_other";

  private final AuditService auditService;
  private final TenantProvisioningService provisioningSvc;

  AuditPdfExporterIntegrationTest(
      AuditService auditService, TenantProvisioningService provisioningSvc) {
    this.auditService = auditService;
    this.provisioningSvc = provisioningSvc;
  }

  private String schemaName;
  private String otherSchemaName;
  private UUID liveMemberId;
  private UUID otherOrgMemberId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    schemaName =
        provisioningSvc.provisionTenant(ORG_ID, "Audit PDF Export Test Org", null).schemaName();
    var liveMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_pdf_owner", "pdf_owner@test.com", "PDF Owner", "owner");
    liveMemberId = UUID.fromString(liveMemberIdStr);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pdf_member", "pdf_member@test.com", "PDF Member", "member");

    // Provision a second tenant + owner used by the tenant-isolation test below.
    otherSchemaName =
        provisioningSvc
            .provisionTenant(OTHER_ORG_ID, "Audit PDF Export Other Org", null)
            .schemaName();
    var otherMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc,
            OTHER_ORG_ID,
            "user_pdf_owner_other",
            "pdf_owner_other@test.com",
            "PDF Owner Other",
            "owner");
    otherOrgMemberId = UUID.fromString(otherMemberIdStr);

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
                      "task.created",
                      "task",
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
            });
  }

  private MvcResult performExport(String clerkUserId, String... params) throws Exception {
    var req =
        get("/api/audit-events/export.pdf").with(TestJwtFactory.ownerJwt(ORG_ID, clerkUserId));
    for (int i = 0; i + 1 < params.length; i += 2) {
      req = req.param(params[i], params[i + 1]);
    }
    return mockMvc.perform(req).andReturn();
  }

  @Test
  void successfulExportReturnsPdfBytesAndAttachmentHeader() throws Exception {
    // Filter to the small seeded baseline so this assertion is independent of any cap-test
    // seeding that may have run earlier in this PER_CLASS lifecycle.
    var result = performExport("user_pdf_owner", "entityType", "task");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    String contentType = result.getResponse().getContentType();
    assertThat(contentType).startsWith("application/pdf");

    String disposition = result.getResponse().getHeader("Content-Disposition");
    assertThat(disposition).startsWith("attachment; filename=\"audit-events-");
    assertThat(disposition).endsWith(".pdf\"");

    byte[] body = result.getResponse().getContentAsByteArray();
    assertThat(body).isNotEmpty();
    // PDF magic header — the openhtmltopdf renderer always emits "%PDF-" as the first 5 bytes.
    String prefix = new String(body, 0, Math.min(5, body.length));
    assertThat(prefix).isEqualTo("%PDF-");
  }

  @Test
  void successfulExportEmitsReflexiveEventWithFormatPdf() throws Exception {
    int beforeCount = countExportEvents();

    performExport("user_pdf_owner", "entityType", "task");

    var events = findExportEvents();
    assertThat(events).hasSize(beforeCount + 1);

    var latest = events.get(0);
    var details = latest.getDetails();
    assertThat(details).isNotNull();
    assertThat(details.get("format")).isEqualTo("PDF");
    assertThat(details.get("rowCount")).isNotNull();
    long rowCount = ((Number) details.get("rowCount")).longValue();
    assertThat(rowCount).isGreaterThanOrEqualTo(1);

    @SuppressWarnings("unchecked")
    Map<String, Object> filterMap = (Map<String, Object>) details.get("filter");
    assertThat(filterMap).isNotNull();
    assertThat(filterMap).containsEntry("entityType", "task");

    assertThat(latest.getEntityType()).isEqualTo("audit_export");
    assertThat(latest.getEntityId()).isNotNull();
  }

  @Test
  void overCapRequestReturnsProblemDetail413AndNoReflexiveEvent() throws Exception {
    // Seed > 10_000 rows scoped to a unique entityId so we can target the pre-flight count
    // without polluting other tests' result sets. Run inside a single ScopedValue block so the
    // Hibernate session reuses one connection for the whole burst.
    int seedCount = 10_001;
    UUID entityId = UUID.randomUUID();
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              for (int i = 0; i < seedCount; i++) {
                auditService.log(
                    new AuditEventRecord(
                        "pdf.cap.seed",
                        "pdf_cap_seed",
                        entityId,
                        liveMemberId,
                        "USER",
                        "API",
                        null,
                        null,
                        null));
              }
            });

    int beforeReflexive = countExportEvents();

    mockMvc
        .perform(
            get("/api/audit-events/export.pdf")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pdf_owner"))
                .param("entityType", "pdf_cap_seed")
                .param("entityId", entityId.toString()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.status").value(413))
        .andExpect(jsonPath("$.rowCount").value(seedCount))
        .andExpect(jsonPath("$.cap").value(10000));

    // ADR-264: failed exports MUST NOT emit the reflexive event.
    assertThat(countExportEvents()).isEqualTo(beforeReflexive);
  }

  @Test
  void capabilityGateRejectsNonOwner() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/export.pdf")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pdf_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  /**
   * Tenant isolation guard: seed a uniquely-marked event in a second tenant's schema, then export
   * as the first tenant's owner and assert the other tenant's marker text never appears in the
   * rendered PDF. This catches any regression where the export bypasses the {@link
   * RequestScopes#TENANT_ID} schema binding (e.g. a missing {@code @Transactional} or a stray
   * cross-schema query).
   */
  @Test
  void exportFromOrgADoesNotLeakOrgBEvents() throws Exception {
    // Seed a distinctive entityType in BOTH tenants so we can prove org A's export sees only its
    // own row even when the same entityType exists in another tenant's schema.
    String sharedEntityType = "tenant_iso_pdf_marker";
    UUID orgAEntityId = UUID.randomUUID();
    UUID orgBEntityId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        "task.created",
                        sharedEntityType,
                        orgAEntityId,
                        liveMemberId,
                        "USER",
                        "API",
                        null,
                        null,
                        null)));

    ScopedValue.where(RequestScopes.TENANT_ID, otherSchemaName)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        "task.created",
                        sharedEntityType,
                        orgBEntityId,
                        otherOrgMemberId,
                        "USER",
                        "API",
                        null,
                        null,
                        null)));

    var result = performExport("user_pdf_owner", "entityType", sharedEntityType);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    byte[] pdfBytes = result.getResponse().getContentAsByteArray();
    assertThat(pdfBytes).isNotEmpty();

    String text;
    try (var doc = Loader.loadPDF(pdfBytes)) {
      text = new PDFTextStripper().getText(doc);
    }
    // Strip whitespace — PDFBox introduces soft line breaks inside narrow table cells, which
    // splits long UUIDs across multiple text-stream segments. The collapsed form is what we
    // want to assert against.
    String collapsed = text.replaceAll("\\s+", "");

    // Org A's entityId must appear; org B's entityId must NOT appear in the rendered body.
    assertThat(collapsed)
        .as("PDF rendered for org A must contain org A's seeded entity id")
        .contains(orgAEntityId.toString());
    assertThat(collapsed)
        .as("PDF rendered for org A must NOT leak org B's entity id")
        .doesNotContain(orgBEntityId.toString());

    // Defence in depth: confirm only one row of this entityType is visible to org A via the
    // service-layer query — if this drifts from the PDF assertion above, both will fail and
    // point at the same regression.
    int visibleToOrgA =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
            .call(
                () ->
                    auditService
                        .findEvents(
                            new AuditEventFilter(
                                sharedEntityType, null, null, null, null, null, null),
                            PageRequest.of(0, 100))
                        .getContent()
                        .size());
    assertThat(visibleToOrgA).isEqualTo(1);
  }

  private int countExportEvents() {
    return findExportEvents().size();
  }

  private List<AuditEvent> findExportEvents() {
    return ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .call(
            () ->
                auditService
                    .findEvents(
                        new AuditEventFilter(
                            "audit_export", null, null, "audit.export.generated", null, null, null),
                        PageRequest.of(0, 100))
                    .getContent());
  }
}
