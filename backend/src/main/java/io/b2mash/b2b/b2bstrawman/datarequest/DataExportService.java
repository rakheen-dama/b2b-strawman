package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.config.S3Config.S3Properties;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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
  private final S3Client s3Client;
  private final S3Properties s3Properties;
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
      S3Client s3Client,
      S3Properties s3Properties,
      ObjectMapper objectMapper,
      AuditService auditService) {
    this.requestRepository = requestRepository;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.documentRepository = documentRepository;
    this.invoiceRepository = invoiceRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.commentRepository = commentRepository;
    this.s3Client = s3Client;
    this.s3Properties = s3Properties;
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

  @Transactional
  public String generateExport(UUID requestId, UUID actorId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("DataSubjectRequest", requestId));

    UUID customerId = request.getCustomerId();

    // Collect all data sections
    Map<String, Object> data = collectData(customerId);

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

    // Upload to S3
    String s3Key = "org/" + RequestScopes.TENANT_ID.get() + "/exports/" + requestId + ".zip";

    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(s3Properties.bucketName())
            .key(s3Key)
            .contentType("application/zip")
            .build();
    s3Client.putObject(putRequest, RequestBody.fromBytes(zipBytes));

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

  private Map<String, Object> collectData(UUID customerId) {
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

    // Time entries (billable, via customer's projects) — single batch query
    List<UUID> projectIds = customerProjects.stream().map(CustomerProject::getProjectId).toList();
    List<ExportTimeEntryData> timeEntryDtos;
    if (projectIds.isEmpty()) {
      timeEntryDtos = List.of();
    } else {
      var timeEntries = timeEntryRepository.findBillableByProjectIdIn(projectIds);
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

    return data;
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
