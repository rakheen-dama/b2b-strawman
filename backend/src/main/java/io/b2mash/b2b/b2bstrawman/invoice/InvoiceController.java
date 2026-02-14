package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateLineItemRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

  private final InvoiceService invoiceService;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final ProjectRepository projectRepository;
  private final ITemplateEngine templateEngine;

  public InvoiceController(
      InvoiceService invoiceService,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      ProjectRepository projectRepository,
      ITemplateEngine templateEngine) {
    this.invoiceService = invoiceService;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.projectRepository = projectRepository;
    this.templateEngine = templateEngine;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> createDraft(
      @Valid @RequestBody CreateInvoiceRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var response = invoiceService.createDraft(request, createdBy);
    return ResponseEntity.created(URI.create("/api/invoices/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> updateDraft(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvoiceRequest request) {
    return ResponseEntity.ok(invoiceService.updateDraft(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteDraft(@PathVariable UUID id) {
    invoiceService.deleteDraft(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.findById(id));
  }

  @GetMapping("/{id}/preview")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  @Transactional(readOnly = true)
  public ResponseEntity<String> preview(@PathVariable UUID id) {
    var invoice =
        invoiceRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));

    var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(id);

    // Batch-fetch project names
    var projectIds =
        lines.stream().map(InvoiceLine::getProjectId).filter(Objects::nonNull).distinct().toList();

    Map<UUID, String> projectNames;
    if (!projectIds.isEmpty()) {
      projectNames =
          projectRepository.findAllByIds(projectIds).stream()
              .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));
    } else {
      projectNames = Map.of();
    }

    // Group lines by project name (preserving insertion order)
    // Lines with null projectId go under "Other Items"
    var groupedLines = new LinkedHashMap<String, List<InvoiceLine>>();
    var grouped =
        lines.stream()
            .collect(
                Collectors.groupingBy(
                    line -> {
                      if (line.getProjectId() == null) {
                        return "Other Items";
                      }
                      return projectNames.getOrDefault(line.getProjectId(), "Unknown Project");
                    },
                    LinkedHashMap::new,
                    Collectors.toList()));

    // Move "Other Items" to the end if present
    if (grouped.containsKey("Other Items")) {
      var otherItems = grouped.remove("Other Items");
      groupedLines.putAll(grouped);
      groupedLines.put("Other Items", otherItems);
    } else {
      groupedLines.putAll(grouped);
    }

    // Precompute per-group subtotals
    var groupSubtotals = new LinkedHashMap<String, BigDecimal>();
    for (var entry : groupedLines.entrySet()) {
      BigDecimal subtotal =
          entry.getValue().stream()
              .map(InvoiceLine::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      groupSubtotals.put(entry.getKey(), subtotal);
    }

    Context ctx = new Context();
    ctx.setVariable("invoice", invoice);
    ctx.setVariable("groupedLines", groupedLines);
    ctx.setVariable("groupSubtotals", groupSubtotals);

    String html = templateEngine.process("invoice-preview", ctx);
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<InvoiceResponse>> listInvoices(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) UUID projectId) {
    return ResponseEntity.ok(invoiceService.findAll(customerId, status, projectId));
  }

  // --- Line item CRUD ---

  @PostMapping("/{id}/lines")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> addLineItem(
      @PathVariable UUID id, @Valid @RequestBody AddLineItemRequest request) {
    var response = invoiceService.addLineItem(id, request);
    return ResponseEntity.created(URI.create("/api/invoices/" + id)).body(response);
  }

  @PutMapping("/{id}/lines/{lineId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> updateLineItem(
      @PathVariable UUID id,
      @PathVariable UUID lineId,
      @Valid @RequestBody UpdateLineItemRequest request) {
    return ResponseEntity.ok(invoiceService.updateLineItem(id, lineId, request));
  }

  @DeleteMapping("/{id}/lines/{lineId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteLineItem(@PathVariable UUID id, @PathVariable UUID lineId) {
    invoiceService.deleteLineItem(id, lineId);
    return ResponseEntity.noContent().build();
  }

  // --- Lifecycle transitions ---

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> approveInvoice(@PathVariable UUID id) {
    UUID approvedBy = RequestScopes.requireMemberId();
    return ResponseEntity.ok(invoiceService.approve(id, approvedBy));
  }

  @PostMapping("/{id}/send")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> sendInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.send(id));
  }

  @PostMapping("/{id}/payment")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> recordPayment(
      @PathVariable UUID id, @RequestBody(required = false) RecordPaymentRequest request) {
    String paymentReference = request != null ? request.paymentReference() : null;
    return ResponseEntity.ok(invoiceService.recordPayment(id, paymentReference));
  }

  @PostMapping("/{id}/void")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> voidInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.voidInvoice(id));
  }
}
