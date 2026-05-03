package io.b2mash.b2b.b2bstrawman.audit.export;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventMetadataResolver;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Streaming RFC 4180 CSV writer for the firm-wide audit log. Pulls rows lazily from {@link
 * AuditService#streamEvents(AuditEventFilter)} and flushes them through the supplied {@link
 * OutputStream} so memory stays bounded regardless of result size. Per-row enrichment (label,
 * severity, actor display name) goes through {@link AuditEventMetadataResolver#enrich}.
 *
 * <p>Caller MUST invoke {@link #writeCsv} inside an active read-only transaction so the underlying
 * Hibernate {@code Stream<>} cursor stays open across the iteration.
 */
@Component
public class AuditCsvExporter {

  static final String HEADER =
      "occurredAt,eventType,label,severity,entityType,entityId,actorId,actorDisplayName,"
          + "actorType,source,ipAddress,userAgent,detailsJson";

  private static final String CRLF = "\r\n";

  private final AuditService auditService;
  private final AuditEventMetadataResolver metadataResolver;
  private final ObjectMapper objectMapper;

  public AuditCsvExporter(
      AuditService auditService,
      AuditEventMetadataResolver metadataResolver,
      ObjectMapper objectMapper) {
    this.auditService = auditService;
    this.metadataResolver = metadataResolver;
    this.objectMapper = objectMapper;
  }

  /**
   * Streams CSV rows for events matching {@code filter} to {@code out}. Returns the data row count
   * (excluding the header). The caller's transaction must remain open while this method runs.
   *
   * @param filter audit event filter (same shape as the list endpoint)
   * @param out target stream — closed by the caller (StreamingResponseBody manages its lifecycle)
   * @return the number of data rows written
   */
  public long writeCsv(AuditEventFilter filter, OutputStream out) throws IOException {
    try (Stream<AuditEvent> stream = auditService.streamEvents(filter)) {
      return writeCsv(stream, out);
    }
  }

  /**
   * Streaming overload that writes CSV rows for a pre-fetched event stream — used by DSAR
   * audit-trail export (Epic 505A) where the caller has already opened a customer-scoped stream via
   * {@link AuditService#findEventsForCustomer(UUID)}.
   *
   * <p>This overload does NOT close the stream (the caller's try-with-resources owns it) and does
   * NOT close the {@link OutputStream} (the caller manages the underlying ZipEntry lifecycle). It
   * does flush the writer after each chunk and at the end so all rows reach the underlying stream
   * before the caller closes the ZipEntry.
   *
   * <p>Caller MUST be inside an active read-only transaction (Hibernate cursor contract).
   *
   * @param stream open audit-event stream (caller closes)
   * @param out target stream (caller closes)
   * @return the number of data rows written (excluding header)
   */
  public long writeCsv(Stream<AuditEvent> stream, OutputStream out) throws IOException {
    var writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    long rowCount = 0L;
    writer.write(HEADER);
    writer.write(CRLF);
    var iterator = stream.iterator();
    while (iterator.hasNext()) {
      var enriched = metadataResolver.enrich(iterator.next());
      writeRow(writer, enriched);
      rowCount++;
      if (rowCount % 1024 == 0) {
        writer.flush();
      }
    }
    writer.flush();
    return rowCount;
  }

  private void writeRow(
      BufferedWriter writer, AuditEventMetadataResolver.EnrichedAuditEvent enriched)
      throws IOException {
    var event = enriched.event();
    var metadata = enriched.metadata();
    writer.write(escape(formatInstant(event.getOccurredAt())));
    writer.write(',');
    writer.write(escape(event.getEventType()));
    writer.write(',');
    writer.write(escape(metadata == null ? "" : metadata.label()));
    writer.write(',');
    writer.write(
        escape(metadata == null || metadata.severity() == null ? "" : metadata.severity().name()));
    writer.write(',');
    writer.write(escape(event.getEntityType()));
    writer.write(',');
    writer.write(escape(formatUuid(event.getEntityId())));
    writer.write(',');
    writer.write(escape(formatUuid(event.getActorId())));
    writer.write(',');
    writer.write(escape(enriched.actorDisplayName()));
    writer.write(',');
    writer.write(escape(event.getActorType()));
    writer.write(',');
    writer.write(escape(event.getSource()));
    writer.write(',');
    writer.write(escape(event.getIpAddress()));
    writer.write(',');
    writer.write(escape(event.getUserAgent()));
    writer.write(',');
    writer.write(escape(serializeDetails(event.getDetails())));
    writer.write(CRLF);
  }

  private static String formatInstant(Instant instant) {
    return instant == null ? "" : instant.toString();
  }

  private static String formatUuid(UUID uuid) {
    return uuid == null ? "" : uuid.toString();
  }

  private String serializeDetails(Map<String, Object> details) {
    if (details == null) {
      return "";
    }
    try {
      return objectMapper.writeValueAsString(details);
    } catch (JacksonException e) {
      // Deterministic fallback so a single bad row doesn't fail the whole export. The Map came
      // from JSONB so this realistically can't trigger, but defending against it keeps the export
      // robust for forensic use.
      return "";
    }
  }

  /**
   * RFC 4180 escaping. Wraps the field in double quotes when it contains a comma, double quote,
   * carriage return, or newline; doubles internal double quotes inside the wrapped field. Returns
   * the empty string for {@code null}.
   */
  static String escape(String value) {
    if (value == null) {
      return "";
    }
    boolean needsQuoting =
        value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\r') >= 0
            || value.indexOf('\n') >= 0;
    if (!needsQuoting) {
      return value;
    }
    var sb = new StringBuilder(value.length() + 4);
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '"') {
        sb.append('"').append('"');
      } else {
        sb.append(c);
      }
    }
    sb.append('"');
    return sb.toString();
  }
}
