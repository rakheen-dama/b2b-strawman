package io.b2mash.b2b.b2bstrawman.audit.export;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventMetadataResolver;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventMetadataResolver.EnrichedAuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.audit.AuditSeverity;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.template.TiptapRenderer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders an audit-event PDF via the existing Tiptap → openhtmltopdf pipeline (ADR-263 / Phase
 * 12/31/42). Loads the {@code audit-export} Tiptap template from the classpath, materialises
 * enriched rows into the rendering context, walks the JSON tree via {@link TiptapRenderer} to
 * produce HTML, and converts that HTML to PDF bytes via {@link PdfRenderingService#htmlToPdf}.
 *
 * <p>Memory bound: the controller enforces a 10,000-row pre-flight cap before this exporter is
 * called, so in-memory rendering is acceptable per ADR-263 (no streaming PDF beyond chunked output
 * within the cap).
 */
@Component
public class AuditPdfExporter {

  /** Maximum row cap for the PDF export — also enforced in the controller pre-flight count. */
  public static final long MAX_ROWS = 10_000L;

  private static final DateTimeFormatter ISO_INSTANT_DISPLAY = DateTimeFormatter.ISO_INSTANT;

  private final AuditService auditService;
  private final AuditEventMetadataResolver metadataResolver;
  private final TiptapRenderer tiptapRenderer;
  private final PdfRenderingService pdfRenderingService;
  private final ObjectMapper objectMapper;
  private final Resource templateResource;
  private volatile Map<String, Object> cachedTemplate;

  public AuditPdfExporter(
      AuditService auditService,
      AuditEventMetadataResolver metadataResolver,
      TiptapRenderer tiptapRenderer,
      PdfRenderingService pdfRenderingService,
      ObjectMapper objectMapper,
      @Value("classpath:templates/audit-export.tiptap.json") Resource templateResource) {
    this.auditService = auditService;
    this.metadataResolver = metadataResolver;
    this.tiptapRenderer = tiptapRenderer;
    this.pdfRenderingService = pdfRenderingService;
    this.objectMapper = objectMapper;
    this.templateResource = templateResource;
  }

  /**
   * Renders the PDF and writes it to {@code out}. Returns the actual rendered row count so the
   * caller can record it on the reflexive audit event. Caller MUST already be inside an active
   * transaction so {@link AuditService#streamEvents} keeps its cursor open.
   *
   * @param filter audit-event filter (same as list / CSV endpoints)
   * @param exportedBy display label for the actor running the export
   * @param out target stream (typically the {@code StreamingResponseBody}'s output)
   * @return number of rows rendered into the PDF body
   */
  public long writePdf(AuditEventFilter filter, String exportedBy, OutputStream out)
      throws IOException {
    List<Map<String, Object>> rows = collectRows(filter);
    Map<String, Object> context = buildContext(filter, exportedBy, rows);
    Map<String, Object> template = loadTemplate();
    String html = tiptapRenderer.render(template, context, Map.of(), null);
    byte[] pdf = pdfRenderingService.htmlToPdf(html);
    out.write(pdf);
    out.flush();
    return rows.size();
  }

  private List<Map<String, Object>> collectRows(AuditEventFilter filter) {
    try (Stream<AuditEvent> stream = auditService.streamEvents(filter)) {
      List<AuditEvent> events = stream.limit(MAX_ROWS).collect(Collectors.toList());
      List<EnrichedAuditEvent> enriched = metadataResolver.enrich(events);
      List<Map<String, Object>> rows = new ArrayList<>(enriched.size());
      for (EnrichedAuditEvent e : enriched) {
        rows.add(toRow(e));
      }
      return rows;
    }
  }

  private Map<String, Object> toRow(EnrichedAuditEvent enriched) {
    var event = enriched.event();
    var metadata = enriched.metadata();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put(
        "occurredAt",
        event.getOccurredAt() != null ? ISO_INSTANT_DISPLAY.format(event.getOccurredAt()) : "");
    row.put("eventType", event.getEventType() != null ? event.getEventType() : "");
    row.put("label", metadata != null ? metadata.label() : event.getEventType());
    row.put(
        "severity",
        metadata != null && metadata.severity() != null ? metadata.severity().name() : "");
    row.put("entityType", event.getEntityType() != null ? event.getEntityType() : "");
    row.put("entityId", event.getEntityId() != null ? event.getEntityId().toString() : "");
    row.put(
        "actorDisplayName", enriched.actorDisplayName() != null ? enriched.actorDisplayName() : "");
    row.put("actorType", event.getActorType() != null ? event.getActorType() : "");
    row.put("detailsJson", serialiseDetails(event.getDetails()));
    return row;
  }

  private String serialiseDetails(Map<String, Object> details) {
    if (details == null || details.isEmpty()) {
      return "";
    }
    try {
      return objectMapper.writeValueAsString(details);
    } catch (RuntimeException e) {
      // Defensive: a row with a non-serialisable details payload should not abort the whole PDF.
      return "{\"_error\":\"unserialisable details\"}";
    }
  }

  private Map<String, Object> buildContext(
      AuditEventFilter filter, String exportedBy, List<Map<String, Object>> rows) {
    Map<String, Object> ctx = new LinkedHashMap<>();
    Map<String, Object> org = new LinkedHashMap<>();
    String orgId = RequestScopes.getOrgIdOrNull();
    org.put("name", orgId != null ? orgId : "");
    org.put("logoUrl", "");
    ctx.put("org", org);

    Map<String, Object> filterCtx = new LinkedHashMap<>();
    filterCtx.put("dateRange", formatDateRange(filter));
    filterCtx.put("summary", formatFilterSummary(filter));
    ctx.put("filter", filterCtx);

    ctx.put("generatedAt", ISO_INSTANT_DISPLAY.format(Instant.now()));
    ctx.put("exportedBy", exportedBy != null ? exportedBy : "Unknown");
    ctx.put("tenantHash", computeTenantHash());
    ctx.put("rowCount", rows.size());
    ctx.put("rows", rows);
    return ctx;
  }

  static String formatDateRange(AuditEventFilter filter) {
    String from =
        filter.from() != null
            ? LocalDate.ofInstant(filter.from(), ZoneOffset.UTC).toString()
            : "all";
    String to =
        filter.to() != null
            ? LocalDate.ofInstant(filter.to(), ZoneOffset.UTC).toString()
            : LocalDate.now(ZoneOffset.UTC).toString();
    return from + " to " + to;
  }

  static String formatFilterSummary(AuditEventFilter filter) {
    List<String> parts = new ArrayList<>();
    if (filter.entityType() != null) {
      parts.add("entityType=" + filter.entityType());
    }
    if (filter.entityId() != null) {
      parts.add("entityId=" + filter.entityId());
    }
    if (filter.actorId() != null) {
      parts.add("actorId=" + filter.actorId());
    }
    if (filter.eventType() != null) {
      parts.add("eventType=" + filter.eventType());
    }
    Set<AuditSeverity> sev = filter.severities();
    if (sev != null && !sev.isEmpty()) {
      parts.add(
          "severities=" + sev.stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
    }
    return parts.isEmpty() ? "(none)" : String.join("; ", parts);
  }

  private static String computeTenantHash() {
    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : "unknown";
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(tenantId.getBytes(StandardCharsets.UTF_8));
      // Short stable identifier — first 8 hex chars are enough for the footer disambiguator.
      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < 4 && i < digest.length; i++) {
        hex.append(String.format("%02x", digest[i]));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      return "unknown";
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadTemplate() {
    Map<String, Object> local = cachedTemplate;
    if (local != null) {
      return local;
    }
    synchronized (this) {
      if (cachedTemplate != null) {
        return cachedTemplate;
      }
      try (var in = templateResource.getInputStream()) {
        Map<String, Object> parsed =
            objectMapper.readValue(in, new TypeReference<Map<String, Object>>() {});
        cachedTemplate = parsed;
        return parsed;
      } catch (IOException e) {
        throw new UncheckedIOException(
            "Failed to load audit-export Tiptap template from classpath", e);
      }
    }
  }
}
