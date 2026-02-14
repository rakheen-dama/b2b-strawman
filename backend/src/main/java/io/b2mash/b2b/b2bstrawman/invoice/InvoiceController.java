package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for invoice CRUD operations. Endpoints require authenticated users with at least
 * ORG_MEMBER role. Fine-grained permission checks (admin/owner vs lead/creator) are enforced in the
 * service layer.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

  private static final Set<String> ALLOWED_SORT_FIELDS =
      Set.of("createdAt", "updatedAt", "issueDate", "dueDate", "total", "status", "invoiceNumber");

  private final InvoiceService invoiceService;
  private final ProjectRepository projectRepository;

  public InvoiceController(InvoiceService invoiceService, ProjectRepository projectRepository) {
    this.invoiceService = invoiceService;
    this.projectRepository = projectRepository;
  }

  // --- Invoice CRUD ---

  /**
   * Creates a new draft invoice from time entries.
   *
   * @param request the creation request with customer, currency, time entry IDs, and optional
   *     fields
   * @return 201 Created with the invoice response
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> createInvoice(
      @Valid @RequestBody CreateInvoiceRequest request) {

    UUID memberId = RequestScopes.requireMemberId();

    Invoice invoice =
        invoiceService.createDraft(
            request.customerId(),
            request.currency(),
            request.timeEntryIds(),
            request.dueDate(),
            request.notes(),
            request.paymentTerms(),
            memberId);

    List<InvoiceLine> lines = invoiceService.getInvoiceLines(invoice.getId());
    var projectNames = resolveProjectNames(lines);
    InvoiceResponse response = InvoiceResponse.from(invoice, lines, projectNames);

    return ResponseEntity.created(URI.create("/api/invoices/" + invoice.getId())).body(response);
  }

  /**
   * Updates a draft invoice's editable fields.
   *
   * @param id the invoice ID
   * @param request the update request with optional fields
   * @return 200 OK with the updated invoice response
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> updateInvoice(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvoiceRequest request) {

    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    // Permission check: admin/owner or creator
    Invoice existing = invoiceService.getInvoice(id);
    invoiceService.requirePermission(existing, memberId, orgRole);

    Invoice invoice =
        invoiceService.updateDraft(
            id, request.dueDate(), request.notes(), request.paymentTerms(), request.taxAmount());

    List<InvoiceLine> lines = invoiceService.getInvoiceLines(invoice.getId());
    var projectNames = resolveProjectNames(lines);

    return ResponseEntity.ok(InvoiceResponse.from(invoice, lines, projectNames));
  }

  /**
   * Deletes a draft invoice and all its lines.
   *
   * @param id the invoice ID
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteInvoice(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    Invoice existing = invoiceService.getInvoice(id);
    invoiceService.requirePermission(existing, memberId, orgRole);

    invoiceService.deleteDraft(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Retrieves a single invoice by ID, including its lines.
   *
   * @param id the invoice ID
   * @return 200 OK with the invoice response
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable UUID id) {
    Invoice invoice = invoiceService.getInvoice(id);
    List<InvoiceLine> lines = invoiceService.getInvoiceLines(invoice.getId());
    var projectNames = resolveProjectNames(lines);

    return ResponseEntity.ok(InvoiceResponse.from(invoice, lines, projectNames));
  }

  /**
   * Lists invoices with optional filters and pagination.
   *
   * @param customerId optional customer ID filter
   * @param status optional status filter
   * @param from optional issue date from (inclusive)
   * @param to optional issue date to (inclusive)
   * @param page page number (0-based)
   * @param size page size (max 100)
   * @param sort sort field and direction (e.g., "createdAt,desc")
   * @return 200 OK with paginated invoice list
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceListResponse> listInvoices(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {

    int cappedSize = Math.min(size, 100);
    Pageable pageable = parsePageable(page, cappedSize, sort);

    Page<Invoice> invoicePage = invoiceService.listInvoices(customerId, status, from, to, pageable);

    List<InvoiceSummaryResponse> content =
        invoicePage.getContent().stream().map(InvoiceSummaryResponse::from).toList();

    return ResponseEntity.ok(
        new InvoiceListResponse(
            content,
            invoicePage.getTotalElements(),
            invoicePage.getTotalPages(),
            invoicePage.getNumber(),
            invoicePage.getSize()));
  }

  // --- Line Item Endpoints ---

  /**
   * Adds a manual line item to a draft invoice.
   *
   * @param id the invoice ID
   * @param request the line item request
   * @return 201 Created with the updated invoice response
   */
  @PostMapping("/{id}/lines")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> addLine(
      @PathVariable UUID id, @Valid @RequestBody AddLineRequest request) {

    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    Invoice existing = invoiceService.getInvoice(id);
    invoiceService.requirePermission(existing, memberId, orgRole);

    Invoice invoice =
        invoiceService.addLine(
            id,
            request.projectId(),
            request.description(),
            request.quantity(),
            request.unitPrice(),
            request.sortOrder());

    List<InvoiceLine> lines = invoiceService.getInvoiceLines(invoice.getId());
    var projectNames = resolveProjectNames(lines);

    return ResponseEntity.created(URI.create("/api/invoices/" + id + "/lines"))
        .body(InvoiceResponse.from(invoice, lines, projectNames));
  }

  /**
   * Updates a line item on a draft invoice.
   *
   * @param id the invoice ID
   * @param lineId the line ID
   * @param request the update request
   * @return 200 OK with the updated invoice response
   */
  @PutMapping("/{id}/lines/{lineId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> updateLine(
      @PathVariable UUID id,
      @PathVariable UUID lineId,
      @Valid @RequestBody UpdateLineRequest request) {

    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    Invoice existing = invoiceService.getInvoice(id);
    invoiceService.requirePermission(existing, memberId, orgRole);

    Invoice invoice =
        invoiceService.updateLine(
            id,
            lineId,
            request.description(),
            request.quantity(),
            request.unitPrice(),
            request.sortOrder());

    List<InvoiceLine> lines = invoiceService.getInvoiceLines(invoice.getId());
    var projectNames = resolveProjectNames(lines);

    return ResponseEntity.ok(InvoiceResponse.from(invoice, lines, projectNames));
  }

  /**
   * Removes a line item from a draft invoice.
   *
   * @param id the invoice ID
   * @param lineId the line ID to remove
   * @return 204 No Content
   */
  @DeleteMapping("/{id}/lines/{lineId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    Invoice existing = invoiceService.getInvoice(id);
    invoiceService.requirePermission(existing, memberId, orgRole);

    invoiceService.removeLine(id, lineId);
    return ResponseEntity.noContent().build();
  }

  // --- Name Resolution ---

  private Map<UUID, String> resolveProjectNames(List<InvoiceLine> lines) {
    var projectIds =
        lines.stream().map(InvoiceLine::getProjectId).filter(Objects::nonNull).distinct().toList();

    if (projectIds.isEmpty()) {
      return Map.of();
    }

    return projectRepository.findAllById(projectIds).stream()
        .collect(Collectors.toMap(p -> p.getId(), p -> p.getName(), (a, b) -> a));
  }

  private Pageable parsePageable(int page, int size, String sort) {
    String[] parts = sort.split(",");
    String field = ALLOWED_SORT_FIELDS.contains(parts[0]) ? parts[0] : "createdAt";
    Sort.Direction direction =
        parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return PageRequest.of(page, size, Sort.by(direction, field));
  }

  // --- DTOs ---

  public record CreateInvoiceRequest(
      @NotNull(message = "customerId is required") UUID customerId,
      @NotBlank(message = "currency is required")
          @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
          String currency,
      @NotNull(message = "timeEntryIds is required") List<UUID> timeEntryIds,
      LocalDate dueDate,
      String notes,
      String paymentTerms) {}

  public record UpdateInvoiceRequest(
      LocalDate dueDate,
      String notes,
      String paymentTerms,
      @PositiveOrZero(message = "taxAmount must be zero or positive") BigDecimal taxAmount) {}

  public record AddLineRequest(
      UUID projectId,
      @NotBlank(message = "description is required") String description,
      @NotNull(message = "quantity is required") @Positive(message = "quantity must be positive")
          BigDecimal quantity,
      @NotNull(message = "unitPrice is required")
          @PositiveOrZero(message = "unitPrice must be zero or positive")
          BigDecimal unitPrice,
      @PositiveOrZero(message = "sortOrder must be zero or positive") int sortOrder) {}

  public record UpdateLineRequest(
      @NotBlank(message = "description is required") String description,
      @NotNull(message = "quantity is required") @Positive(message = "quantity must be positive")
          BigDecimal quantity,
      @NotNull(message = "unitPrice is required")
          @PositiveOrZero(message = "unitPrice must be zero or positive")
          BigDecimal unitPrice,
      @PositiveOrZero(message = "sortOrder must be zero or positive") int sortOrder) {}

  public record InvoiceResponse(
      UUID id,
      UUID customerId,
      String invoiceNumber,
      InvoiceStatus status,
      String currency,
      LocalDate issueDate,
      LocalDate dueDate,
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal total,
      String notes,
      String paymentTerms,
      String paymentReference,
      Instant paidAt,
      String customerName,
      String customerEmail,
      String customerAddress,
      String orgName,
      UUID createdBy,
      UUID approvedBy,
      Instant createdAt,
      Instant updatedAt,
      List<InvoiceLineResponse> lines) {

    public static InvoiceResponse from(
        Invoice invoice, List<InvoiceLine> lines, Map<UUID, String> projectNames) {
      List<InvoiceLineResponse> lineResponses =
          lines.stream().map(l -> InvoiceLineResponse.from(l, projectNames)).toList();

      return new InvoiceResponse(
          invoice.getId(),
          invoice.getCustomerId(),
          invoice.getInvoiceNumber(),
          invoice.getStatus(),
          invoice.getCurrency(),
          invoice.getIssueDate(),
          invoice.getDueDate(),
          invoice.getSubtotal(),
          invoice.getTaxAmount(),
          invoice.getTotal(),
          invoice.getNotes(),
          invoice.getPaymentTerms(),
          invoice.getPaymentReference(),
          invoice.getPaidAt(),
          invoice.getCustomerName(),
          invoice.getCustomerEmail(),
          invoice.getCustomerAddress(),
          invoice.getOrgName(),
          invoice.getCreatedBy(),
          invoice.getApprovedBy(),
          invoice.getCreatedAt(),
          invoice.getUpdatedAt(),
          lineResponses);
    }
  }

  public record InvoiceLineResponse(
      UUID id,
      UUID invoiceId,
      UUID projectId,
      String projectName,
      UUID timeEntryId,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal amount,
      int sortOrder) {

    public static InvoiceLineResponse from(InvoiceLine line, Map<UUID, String> projectNames) {
      return new InvoiceLineResponse(
          line.getId(),
          line.getInvoiceId(),
          line.getProjectId(),
          line.getProjectId() != null ? projectNames.get(line.getProjectId()) : null,
          line.getTimeEntryId(),
          line.getDescription(),
          line.getQuantity(),
          line.getUnitPrice(),
          line.getAmount(),
          line.getSortOrder());
    }
  }

  public record InvoiceSummaryResponse(
      UUID id,
      UUID customerId,
      String invoiceNumber,
      InvoiceStatus status,
      String currency,
      LocalDate issueDate,
      LocalDate dueDate,
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal total,
      String customerName,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt) {

    public static InvoiceSummaryResponse from(Invoice invoice) {
      return new InvoiceSummaryResponse(
          invoice.getId(),
          invoice.getCustomerId(),
          invoice.getInvoiceNumber(),
          invoice.getStatus(),
          invoice.getCurrency(),
          invoice.getIssueDate(),
          invoice.getDueDate(),
          invoice.getSubtotal(),
          invoice.getTaxAmount(),
          invoice.getTotal(),
          invoice.getCustomerName(),
          invoice.getCreatedBy(),
          invoice.getCreatedAt(),
          invoice.getUpdatedAt());
    }
  }

  public record InvoiceListResponse(
      List<InvoiceSummaryResponse> content,
      long totalElements,
      int totalPages,
      int page,
      int size) {}
}
