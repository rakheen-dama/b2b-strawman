package io.b2mash.b2b.b2bstrawman.audit.export;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.audit.AuditSeverity;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Orchestration layer for the streaming audit-CSV export (Epic 503A). Wraps the {@link
 * AuditCsvExporter} stream-write in a {@link TransactionTemplate#executeWithoutResult} so the
 * underlying Hibernate cursor sees an open transaction throughout {@link
 * StreamingResponseBody#writeTo}, and emits the reflexive {@code audit.export.generated} event
 * immediately after the stream flushes (so the recorded {@code rowCount} is accurate).
 *
 * <p>Per Backend Controller Discipline the controller route is a one-liner that delegates here;
 * filename construction, transaction wrapping, and the reflexive emission all live in this service.
 */
@Service
public class AuditExportService {

  private static final String FILENAME_FALLBACK_TENANT = "unknown";
  private static final String FILENAME_FALLBACK_DATE_FROM = "all";

  private final AuditCsvExporter auditCsvExporter;
  private final AuditPdfExporter auditPdfExporter;
  private final AuditService auditService;
  private final TransactionTemplate transactionTemplate;

  public AuditExportService(
      AuditCsvExporter auditCsvExporter,
      AuditPdfExporter auditPdfExporter,
      AuditService auditService,
      PlatformTransactionManager transactionManager) {
    this.auditCsvExporter = auditCsvExporter;
    this.auditPdfExporter = auditPdfExporter;
    this.auditService = auditService;
    // Read-write tx — the reflexive auditService.log(...) call must persist inside the same
    // transaction as the streaming read so the cursor stays open until the body completes.
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * Builds the streaming CSV response for {@code filter}. The body lambda is invoked by Spring's
   * async dispatch after the controller returns; it runs the streaming write inside a single
   * transaction and emits the reflexive event on success.
   *
   * <p>Spring dispatches {@link StreamingResponseBody} on a virtual thread that does not inherit
   * the controller's {@link ScopedValue} bindings. We snapshot {@code TENANT_ID}, {@code ORG_ID},
   * and {@code MEMBER_ID} at controller-invocation time and re-bind them inside the streaming task
   * so the Hibernate {@code search_path} (tenant) and audit-actor (member) resolve to the caller's
   * identity.
   */
  public ResponseEntity<StreamingResponseBody> streamCsv(AuditEventFilter filter) {
    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.getOrgIdOrNull();
    UUID memberId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;

    // Defence-in-depth: validate tenant/member bindings BEFORE we commit response headers and
    // start streaming. If unbound, throw synchronously so Spring's global handler returns 500
    // instead of leaving the client with a half-written CSV and a 200 status. In practice the
    // @RequiresCapability check on the controller blocks unauthenticated callers, but we defend
    // here too so an unscoped invocation never silently succeeds with a partial body.
    if (tenantId == null || memberId == null) {
      throw new IllegalStateException(
          "Audit export requires a bound tenant and member; refusing to stream unscoped.");
    }

    String filename = buildFilename(filter);
    StreamingResponseBody body =
        outputStream -> {
          Runnable task = () -> writeAndEmit(filter, outputStream);
          RequestScopes.runForTenantWithMember(tenantId, orgId, memberId, task);
        };
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(body);
  }

  private void writeAndEmit(AuditEventFilter filter, java.io.OutputStream outputStream) {
    transactionTemplate.executeWithoutResult(
        status -> {
          long rowCount;
          try {
            rowCount = auditCsvExporter.writeCsv(filter, outputStream);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          // Emit AFTER the stream completes so rowCount is accurate. If the write failed above,
          // we never reach this line and no reflexive event is recorded — matching the "failed
          // exports do NOT emit" contract from ADR-264.
          auditService.log(
              AuditEventBuilder.builder()
                  .eventType("audit.export.generated")
                  .entityType("audit_export")
                  .entityId(UUID.randomUUID())
                  .details(
                      Map.of("filter", filterAsMap(filter), "rowCount", rowCount, "format", "CSV"))
                  .build());
        });
  }

  /**
   * Builds the PDF audit-export response for {@code filter}. Performs a synchronous pre-flight
   * count (Epic 504A) — when over the {@link AuditPdfExporter#MAX_ROWS} cap, returns a 413 {@link
   * ProblemDetail}. Otherwise renders the full PDF in-memory (ADR-263 says in-memory is acceptable
   * within the cap), emits the reflexive {@code audit.export.generated} event with {@code
   * format=PDF}, and returns the PDF bytes as an {@code application/pdf} attachment.
   *
   * <p>Per ADR-264, failed exports (including the 413 cap path) do NOT emit the reflexive event —
   * only successful disclosures are recorded as disclosures.
   */
  public ResponseEntity<?> streamPdf(AuditEventFilter filter) {
    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    UUID memberId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;

    if (tenantId == null || memberId == null) {
      throw new IllegalStateException(
          "Audit PDF export requires a bound tenant and member; refusing to stream unscoped.");
    }

    long preflightCount = auditService.countEvents(filter);
    if (preflightCount > AuditPdfExporter.MAX_ROWS) {
      ProblemDetail problem =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.PAYLOAD_TOO_LARGE,
              "PDF export limited to "
                  + AuditPdfExporter.MAX_ROWS
                  + " events. Narrow the date range or filters.");
      problem.setProperty("rowCount", preflightCount);
      problem.setProperty("cap", AuditPdfExporter.MAX_ROWS);
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
    }

    String exportedBy = "Member " + memberId;
    String filename = buildPdfFilename(filter);
    byte[] pdfBytes = renderAndEmitPdf(filter, exportedBy);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(pdfBytes);
  }

  private byte[] renderAndEmitPdf(AuditEventFilter filter, String exportedBy) {
    return transactionTemplate.execute(
        status -> {
          var buffer = new java.io.ByteArrayOutputStream();
          long rowCount;
          try {
            rowCount = auditPdfExporter.writePdf(filter, exportedBy, buffer);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          // Same ADR-264 contract as CSV: reflexive emit AFTER successful render only.
          auditService.log(
              AuditEventBuilder.builder()
                  .eventType("audit.export.generated")
                  .entityType("audit_export")
                  .entityId(UUID.randomUUID())
                  .details(
                      Map.of("filter", filterAsMap(filter), "rowCount", rowCount, "format", "PDF"))
                  .build());
          return buffer.toByteArray();
        });
  }

  static String buildPdfFilename(AuditEventFilter filter) {
    String csvName = buildFilename(filter);
    return csvName.substring(0, csvName.length() - ".csv".length()) + ".pdf";
  }

  static String buildFilename(AuditEventFilter filter) {
    String rawSlug = RequestScopes.getOrgIdOrNull();
    String tenantSlug = sanitiseSlugForFilename(rawSlug);
    String fromDate =
        filter.from() != null ? formatDate(filter.from()) : FILENAME_FALLBACK_DATE_FROM;
    String toDate = filter.to() != null ? formatDate(filter.to()) : todayUtc();
    return "audit-events-" + tenantSlug + "-" + fromDate + "-" + toDate + ".csv";
  }

  /**
   * Strips any character outside {@code [A-Za-z0-9_-]} from the org slug before it is interpolated
   * into the {@code Content-Disposition} header. This blocks CR/LF/quote injection (a malicious
   * slug could otherwise terminate the header and inject new ones) and keeps the filename
   * filesystem-safe for the downloading client.
   */
  private static String sanitiseSlugForFilename(String slug) {
    if (slug == null || slug.isBlank()) {
      return FILENAME_FALLBACK_TENANT;
    }
    String cleaned = slug.replaceAll("[^A-Za-z0-9_-]", "");
    return cleaned.isEmpty() ? FILENAME_FALLBACK_TENANT : cleaned;
  }

  private static String formatDate(Instant instant) {
    return LocalDate.ofInstant(instant, ZoneOffset.UTC).toString();
  }

  private static String todayUtc() {
    return LocalDate.now(ZoneOffset.UTC).toString();
  }

  /**
   * Flattens the filter into a Map<String,Object> with non-null entries only, keys ordered for
   * stable JSONB layout. Severities are serialised as their {@link AuditSeverity#name()} strings
   * (and sorted) so the recorded event is deterministic.
   */
  static Map<String, Object> filterAsMap(AuditEventFilter filter) {
    Map<String, Object> out = new LinkedHashMap<>();
    putIfNotNull(out, "entityType", filter.entityType());
    putIfNotNull(out, "entityId", filter.entityId());
    putIfNotNull(out, "actorId", filter.actorId());
    putIfNotNull(out, "eventType", filter.eventType());
    putIfNotNull(out, "from", filter.from());
    putIfNotNull(out, "to", filter.to());
    Set<AuditSeverity> severities = filter.severities();
    if (severities != null && !severities.isEmpty()) {
      out.put(
          "severities", severities.stream().map(Enum::name).sorted().collect(Collectors.toList()));
    }
    return out;
  }

  private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value.toString());
    }
  }
}
