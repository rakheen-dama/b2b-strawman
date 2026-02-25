package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InvoiceContextBuilder implements TemplateContextBuilder {

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final TemplateContextHelper contextHelper;

  public InvoiceContextBuilder(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      TemplateContextHelper contextHelper) {
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.contextHelper = contextHelper;
  }

  @Override
  public TemplateEntityType supports() {
    return TemplateEntityType.INVOICE;
  }

  @Override
  public Map<String, Object> buildContext(UUID entityId, UUID memberId) {
    var invoice =
        invoiceRepository
            .findById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", entityId));

    var context = new HashMap<String, Object>();

    // invoice.*
    var invoiceMap = new LinkedHashMap<String, Object>();
    invoiceMap.put("id", invoice.getId());
    invoiceMap.put("invoiceNumber", invoice.getInvoiceNumber());
    invoiceMap.put("status", invoice.getStatus() != null ? invoice.getStatus().name() : null);
    invoiceMap.put(
        "issueDate", invoice.getIssueDate() != null ? invoice.getIssueDate().toString() : null);
    invoiceMap.put(
        "dueDate", invoice.getDueDate() != null ? invoice.getDueDate().toString() : null);
    invoiceMap.put("subtotal", invoice.getSubtotal());
    invoiceMap.put("taxAmount", invoice.getTaxAmount());
    invoiceMap.put("total", invoice.getTotal());
    invoiceMap.put("currency", invoice.getCurrency());
    invoiceMap.put("notes", invoice.getNotes());
    invoiceMap.put(
        "customFields", invoice.getCustomFields() != null ? invoice.getCustomFields() : Map.of());
    context.put("invoice", invoiceMap);

    // lines[]
    var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(entityId);
    var linesList =
        lines.stream()
            .map(
                line -> {
                  var lineMap = new LinkedHashMap<String, Object>();
                  lineMap.put("description", line.getDescription());
                  lineMap.put("quantity", line.getQuantity());
                  lineMap.put("unitPrice", line.getUnitPrice());
                  lineMap.put("amount", line.getAmount());
                  return (Map<String, Object>) lineMap;
                })
            .toList();
    context.put("lines", linesList);

    // customer.* (from invoice's customerId)
    customerRepository
        .findById(invoice.getCustomerId())
        .ifPresentOrElse(
            customer -> {
              var customerMap = new LinkedHashMap<String, Object>();
              customerMap.put("id", customer.getId());
              customerMap.put("name", customer.getName());
              customerMap.put("email", customer.getEmail());
              customerMap.put(
                  "customFields",
                  customer.getCustomFields() != null ? customer.getCustomFields() : Map.of());
              context.put("customer", customerMap);
            },
            () -> context.put("customer", null));

    // project.* (from first invoice line with a projectId, null-safe)
    lines.stream()
        .filter(line -> line.getProjectId() != null)
        .findFirst()
        .ifPresentOrElse(
            line ->
                projectRepository
                    .findById(line.getProjectId())
                    .ifPresentOrElse(
                        project -> {
                          var projectMap = new LinkedHashMap<String, Object>();
                          projectMap.put("id", project.getId());
                          projectMap.put("name", project.getName());
                          projectMap.put(
                              "customFields",
                              project.getCustomFields() != null
                                  ? project.getCustomFields()
                                  : Map.of());
                          context.put("project", projectMap);
                        },
                        () -> context.put("project", null)),
            () -> context.put("project", null));

    // org.*
    context.put("org", contextHelper.buildOrgContext());

    // generatedAt, generatedBy
    context.put("generatedAt", Instant.now().toString());
    context.put("generatedBy", contextHelper.buildGeneratedByMap(memberId));

    return context;
  }
}
