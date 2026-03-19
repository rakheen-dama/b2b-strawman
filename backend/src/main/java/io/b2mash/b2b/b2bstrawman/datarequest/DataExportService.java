package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DataExportService {

  private static final Logger log = LoggerFactory.getLogger(DataExportService.class);

  private final DataSubjectRequestRepository requestRepository;
  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final DocumentRepository documentRepository;
  private final InvoiceRepository invoiceRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final CommentRepository commentRepository;
  private final AuditEventRepository auditEventRepository;
  private final PortalContactRepository portalContactRepository;
  private final StorageService storageService;
  private final ObjectMapper objectMapper;
  private final AuditService auditService;

  public DataExportService(
      DataSubjectRequestRepository requestRepository,
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      DocumentRepository documentRepository,
      InvoiceRepository invoiceRepository,
      TimeEntryRepository timeEntryRepository,
      CommentRepository commentRepository,
      AuditEventRepository auditEventRepository,
      PortalContactRepository portalContactRepository,
      StorageService storageService,
      ObjectMapper objectMapper,
      AuditService auditService) {
    this.requestRepository = requestRepository;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.documentRepository = documentRepository;
    this.invoiceRepository = invoiceRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.commentRepository = commentRepository;
    this.auditEventRepository = auditEventRepository;
    this.portalContactRepository = portalContactRepository;
    this.storageService = storageService;
    this.objectMapper = objectMapper;
    this.auditService = auditService;
  }

  // Export DTO records — prevent raw JPA entity serialization
  private record ExportCustomerData(
      UUID id, String name, String email, String phone, String idNumber, String lifecycleStatus) {}

  private record ExportProjectData(UUID id, UUID projectId) {}

  private record ExportDocumentData(
      UUID id, String fileName, String contentType, String scope, Instant createdAt) {}

  private record ExportInvoiceData(
      UUID id, String invoiceNumber, String status, BigDecimal total, String currency) {}

  private record ExportTimeEntryData(
      UUID id, LocalDate date, int durationMinutes, String description, boolean billable) {}

  private record ExportCommentData(UUID id, String body, String visibility, Instant createdAt) {}

  private record ExportAuditEventData(
      UUID id, String eventType, String entityType, UUID actorId, Instant occurredAt) {}

  private record ExportPortalContactData(
      UUID id, String email, String displayName, String role, String status) {}

  @Transactional
  public String generateExport(UUID requestId, UUID actorId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("DataSubjectRequest", requestId));

    UUID customerId = request.getCustomerId();

    // Collect all data sections (DSAR export: billable time entries only)
    Map<String, Object> data = collectData(customerId, false);

    // Serialize to JSON
    byte[] jsonBytes;
    try {
      jsonBytes = objectMapper.writeValueAsBytes(data);
    } catch (JacksonException e) {
      throw new RuntimeException("Failed to serialize export data to JSON", e);
    }

    // Generate summary CSV
    byte[] csvBytes = generateSummaryCsv(data);

    // Create ZIP
    byte[] zipBytes = generateZip(jsonBytes, csvBytes);

    // Upload to storage
    String s3Key = "org/" + RequestScopes.TENANT_ID.get() + "/exports/" + requestId + ".zip";
    storageService.upload(s3Key, zipBytes, "application/zip");

    // Update request with export file key
    request.setExportFileKey(s3Key);
    requestRepository.save(request);

    // Audit event
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("data.export.generated")
            .entityType("data_subject_request")
            .entityId(requestId)
            .details(Map.of("fileSize", zipBytes.length, "actorId", actorId.toString()))
            .build());

    log.info(
        "Data export generated for request {} — s3Key={}, size={} bytes",
        requestId,
        s3Key,
        zipBytes.length);

    return s3Key;
  }

  /** Triggers a compliance data export and returns the export result for the controller. */
  @Transactional
  public ExportResult triggerCustomerExport(UUID customerId, UUID actorId) {
    var response = exportCustomerData(customerId, actorId);
    return new ExportResult(response.exportId(), response.status(), response.fileCount());
  }

  @Transactional
  public ExportStatusResponse exportCustomerData(UUID customerId, UUID actorId) {
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Collect all data (compliance export: ALL time entries, not just billable)
    Map<String, Object> data = collectData(customerId, true);

    // Generate structured ZIP
    byte[] zipBytes = generateStructuredZip(customerId, data);

    // Count files (non-directory ZIP entries)
    int fileCount = countZipEntries(zipBytes);

    // Generate exportId upfront so it can be embedded in the S3 key
    UUID exportId = UUID.randomUUID();

    // Upload to S3 — use exports segment (matches existing S3_KEY_PATTERN)
    String timestamp = String.valueOf(Instant.now().toEpochMilli());
    String s3Key =
        "org/"
            + RequestScopes.TENANT_ID.get()
            + "/exports/compliance-"
            + customerId
            + "-"
            + exportId
            + "-"
            + timestamp
            + ".zip";
    storageService.upload(s3Key, zipBytes, "application/zip");

    // Generate 24-hour presigned download URL
    var presigned = storageService.generateDownloadUrl(s3Key, Duration.ofHours(24));
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("data.subject.export.generated")
            .entityType("customer")
            .entityId(customerId)
            .details(
                Map.of(
                    "exportId",
                    exportId.toString(),
                    "fileCount",
                    fileCount,
                    "totalSizeBytes",
                    (long) zipBytes.length,
                    "actorId",
                    actorId.toString()))
            .build());

    log.info(
        "Compliance export generated for customer {} — s3Key={}, size={} bytes, files={}",
        customerId,
        s3Key,
        zipBytes.length,
        fileCount);

    return new ExportStatusResponse(
        exportId,
        "COMPLETED",
        presigned.url(),
        presigned.expiresAt(),
        fileCount,
        zipBytes.length,
        s3Key);
  }

  private static final int MAX_EXPORT_LIST_SIZE = 100;

  /**
   * Lists compliance exports from S3. Returns up to {@link #MAX_EXPORT_LIST_SIZE} entries.
   * Presigned download URLs are NOT eagerly generated; callers should use {@link
   * #getExportStatus(UUID)} to obtain a download URL for a specific export.
   */
  public List<ExportStatusResponse> listExports() {
    String tenantId = RequestScopes.requireTenantId();
    String prefix = "org/" + tenantId + "/exports/";
    List<String> keys = storageService.listKeys(prefix);
    return keys.stream()
        .filter(k -> k.contains("/compliance-"))
        .limit(MAX_EXPORT_LIST_SIZE)
        .map(
            k -> {
              UUID extractedId = extractExportIdFromKey(k);
              return new ExportStatusResponse(extractedId, "COMPLETED", null, null, 0, 0L, k);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public ExportStatusResponse getExportStatus(UUID exportId) {
    var matching =
        auditEventRepository
            .findByExportId("data.subject.export.generated", exportId.toString())
            .orElseThrow(() -> new ResourceNotFoundException("Export", exportId));

    UUID customerId = matching.getEntityId();
    String tenantId = RequestScopes.requireTenantId();
    String prefix = "org/" + tenantId + "/exports/";
    List<String> keys = storageService.listKeys(prefix);
    // Match precisely: key must contain the exportId segment
    String exportIdStr = exportId.toString();
    String s3Key =
        keys.stream()
            .filter(
                k ->
                    k.contains("/compliance-" + customerId + "-" + exportIdStr + "-")
                        || k.contains("/compliance-" + customerId + "-" + exportIdStr + "."))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Export", exportId));

    var presigned = storageService.generateDownloadUrl(s3Key, Duration.ofHours(24));
    var details = matching.getDetails();
    int fileCount =
        details != null && details.containsKey("fileCount")
            ? ((Number) details.get("fileCount")).intValue()
            : 0;
    long totalSize =
        details != null && details.containsKey("totalSizeBytes")
            ? ((Number) details.get("totalSizeBytes")).longValue()
            : 0L;

    return new ExportStatusResponse(
        exportId, "COMPLETED", presigned.url(), presigned.expiresAt(), fileCount, totalSize, s3Key);
  }

  /**
   * Extracts the exportId UUID from an S3 key following the pattern: {@code
   * org/{tenantId}/exports/compliance-{customerId}-{exportId}-{timestamp}.zip}. Falls back to a
   * deterministic UUID derived from the key bytes for legacy keys that predate the exportId
   * encoding.
   */
  static UUID extractExportIdFromKey(String key) {
    // Expected filename: compliance-{customerId}-{exportId}-{timestamp}.zip
    String filename = key.substring(key.lastIndexOf('/') + 1);
    // Strip ".zip" suffix
    if (filename.endsWith(".zip")) {
      filename = filename.substring(0, filename.length() - 4);
    }
    // Split: compliance, {customerId}, {exportId}, {timestamp}
    // UUID is 36 chars with dashes; customerId is also a UUID (36 chars)
    // Pattern: "compliance-" prefix, then 36-char customerId, "-", 36-char exportId, "-", timestamp
    String afterPrefix = filename.startsWith("compliance-") ? filename.substring(11) : filename;
    // afterPrefix = "{customerId}-{exportId}-{timestamp}"
    // customerId is 36 chars (UUID), then dash, then exportId is 36 chars
    if (afterPrefix.length() > 73) { // 36 (customerId) + 1 (-) + 36 (exportId)
      String exportIdStr = afterPrefix.substring(37, 73);
      try {
        return UUID.fromString(exportIdStr);
      } catch (IllegalArgumentException e) {
        // Fall through to deterministic UUID
      }
    }
    // Fallback for legacy keys without embedded exportId
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
  }

  private Map<String, Object> collectData(UUID customerId, boolean includeAllTimeEntries) {
    Map<String, Object> data = new LinkedHashMap<>();

    // Customer
    var customer = customerRepository.findById(customerId).orElse(null);
    data.put(
        "customer",
        customer != null
            ? new ExportCustomerData(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getIdNumber(),
                customer.getLifecycleStatus() != null ? customer.getLifecycleStatus().name() : null)
            : null);

    // Projects (via CustomerProject join)
    var customerProjects = customerProjectRepository.findByCustomerId(customerId);
    data.put(
        "projects",
        customerProjects.stream()
            .map(cp -> new ExportProjectData(cp.getId(), cp.getProjectId()))
            .toList());

    // Documents
    var documents = documentRepository.findByCustomerId(customerId);
    data.put(
        "documents",
        documents.stream()
            .map(
                d ->
                    new ExportDocumentData(
                        d.getId(),
                        d.getFileName(),
                        d.getContentType(),
                        d.getScope(),
                        d.getCreatedAt()))
            .toList());

    // Invoices
    var invoices = invoiceRepository.findByCustomerId(customerId);
    data.put(
        "invoices",
        invoices.stream()
            .map(
                i ->
                    new ExportInvoiceData(
                        i.getId(),
                        i.getInvoiceNumber(),
                        i.getStatus() != null ? i.getStatus().name() : null,
                        i.getTotal(),
                        i.getCurrency()))
            .toList());

    // Time entries (via customer's projects) — single batch query
    // includeAllTimeEntries=true uses findByProjectIdIn (compliance export),
    // includeAllTimeEntries=false uses findBillableByProjectIdIn (DSAR export)
    List<UUID> projectIds = customerProjects.stream().map(CustomerProject::getProjectId).toList();
    List<ExportTimeEntryData> timeEntryDtos;
    if (projectIds.isEmpty()) {
      timeEntryDtos = List.of();
    } else {
      var timeEntries =
          includeAllTimeEntries
              ? timeEntryRepository.findByProjectIdIn(projectIds)
              : timeEntryRepository.findBillableByProjectIdIn(projectIds);
      timeEntryDtos =
          timeEntries.stream()
              .map(
                  te ->
                      new ExportTimeEntryData(
                          te.getId(),
                          te.getDate(),
                          te.getDurationMinutes(),
                          te.getDescription(),
                          te.isBillable()))
              .toList();
    }
    data.put("timeEntries", timeEntryDtos);

    // Portal-visible comments
    var comments = commentRepository.findPortalVisibleByCustomerId(customerId);
    data.put(
        "comments",
        comments.stream()
            .map(
                c ->
                    new ExportCommentData(
                        c.getId(), c.getBody(), c.getVisibility(), c.getCreatedAt()))
            .toList());

    // Custom fields — stored as JSONB on the customer entity (no separate table)
    // getCustomFields() can return null for JSONB columns with no value
    var customFields =
        customer != null && customer.getCustomFields() != null
            ? customer.getCustomFields()
            : Map.of();
    data.put("customFields", customFields);

    // Audit events referencing the customer entity
    var auditPage =
        auditEventRepository.findByFilter(
            "customer", customerId, null, null, null, null, Pageable.unpaged());
    data.put(
        "auditEvents",
        auditPage.getContent().stream()
            .map(
                ae ->
                    new ExportAuditEventData(
                        ae.getId(),
                        ae.getEventType(),
                        ae.getEntityType(),
                        ae.getActorId(),
                        ae.getOccurredAt()))
            .toList());

    // Portal contacts
    var portalContacts = portalContactRepository.findByCustomerId(customerId);
    data.put(
        "portalContacts",
        portalContacts.stream()
            .map(
                pc ->
                    new ExportPortalContactData(
                        pc.getId(),
                        pc.getEmail(),
                        pc.getDisplayName(),
                        pc.getRole() != null ? pc.getRole().name() : null,
                        pc.getStatus() != null ? pc.getStatus().name() : null))
            .toList());

    return data;
  }

  private byte[] generateStructuredZip(UUID customerId, Map<String, Object> data) {
    String prefix = "customer-export-" + customerId + "/";
    try (var baos = new ByteArrayOutputStream();
        var zos = new ZipOutputStream(baos)) {

      // Directory entries
      zos.putNextEntry(new ZipEntry(prefix));
      zos.closeEntry();
      zos.putNextEntry(new ZipEntry(prefix + "projects/"));
      zos.closeEntry();

      // customer.json
      writeZipEntry(zos, prefix + "customer.json", data.get("customer"));

      // portal-contacts.json
      writeZipEntry(zos, prefix + "portal-contacts.json", data.get("portalContacts"));

      // projects/project-{id}.json — one per project
      if (data.get("projects") instanceof List<?> projects) {
        for (var project : projects) {
          String projectId = extractProjectId(project);
          writeZipEntry(zos, prefix + "projects/project-" + projectId + ".json", project);
        }
      }

      // Flat files
      writeZipEntry(zos, prefix + "time-entries.json", data.get("timeEntries"));
      writeZipEntry(zos, prefix + "invoices.json", data.get("invoices"));
      writeZipEntry(zos, prefix + "comments.json", data.get("comments"));
      writeZipEntry(zos, prefix + "custom-fields.json", data.get("customFields"));
      writeZipEntry(zos, prefix + "audit-events.json", data.get("auditEvents"));

      // export-metadata.json — do NOT include tenantSchema (internal infrastructure detail)
      var metadata =
          Map.of(
              "exportedAt",
              Instant.now().toString(),
              "orgId",
              RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : "unknown",
              "scope",
              "FULL_CUSTOMER_DATA",
              "customerId",
              customerId.toString());
      writeZipEntry(zos, prefix + "export-metadata.json", metadata);

      zos.finish();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new InvalidStateException(
          "Export generation failed",
          "Failed to generate structured export ZIP for customer " + customerId);
    }
  }

  private void writeZipEntry(ZipOutputStream zos, String name, Object content) throws IOException {
    zos.putNextEntry(new ZipEntry(name));
    try {
      zos.write(objectMapper.writeValueAsBytes(content != null ? content : List.of()));
    } catch (JacksonException e) {
      zos.write("[]".getBytes(StandardCharsets.UTF_8));
    }
    zos.closeEntry();
  }

  private String extractProjectId(Object project) {
    if (project instanceof ExportProjectData epd) {
      return epd.projectId().toString();
    }
    log.warn(
        "Unable to extract project ID from object of type {}, using fallback name",
        project.getClass().getSimpleName());
    return "unknown-project";
  }

  private int countZipEntries(byte[] zipBytes) {
    try (var bais = new ByteArrayInputStream(zipBytes);
        var zis = new ZipInputStream(bais)) {
      int count = 0;
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          count++;
        }
        zis.closeEntry();
      }
      return count;
    } catch (IOException e) {
      log.warn("Failed to count ZIP entries, returning 0", e);
      return 0;
    }
  }

  private byte[] generateSummaryCsv(Map<String, Object> data) {
    var sb = new StringBuilder("section,count\n");
    for (var entry : data.entrySet()) {
      int count =
          switch (entry.getValue()) {
            case java.util.List<?> list -> list.size();
            case null -> 0;
            default -> 1;
          };
      sb.append(entry.getKey()).append(",").append(count).append("\n");
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private byte[] generateZip(byte[] jsonBytes, byte[] csvBytes) {
    try (var baos = new ByteArrayOutputStream();
        var zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry("data.json"));
      zos.write(jsonBytes);
      zos.closeEntry();
      zos.putNextEntry(new ZipEntry("summary.csv"));
      zos.write(csvBytes);
      zos.closeEntry();
      zos.finish();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate export ZIP", e);
    }
  }
}
