package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    contextHelper.populateLocale(context);
    var fieldDefCache = new EnumMap<EntityType, List<FieldDefinition>>(EntityType.class);

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
    // Promoted structural invoice fields (Epic 460) — direct template variables.
    invoiceMap.put("poNumber", invoice.getPoNumber());
    invoiceMap.put("taxType", invoice.getTaxType() != null ? invoice.getTaxType().name() : null);
    invoiceMap.put(
        "billingPeriodStart",
        invoice.getBillingPeriodStart() != null
            ? invoice.getBillingPeriodStart().toString()
            : null);
    invoiceMap.put(
        "billingPeriodEnd",
        invoice.getBillingPeriodEnd() != null ? invoice.getBillingPeriodEnd().toString() : null);
    Map<String, Object> resolvedInvoiceCustomFields =
        contextHelper.resolveDropdownLabels(
            invoice.getCustomFields() != null ? invoice.getCustomFields() : Map.of(),
            EntityType.INVOICE,
            fieldDefCache);
    var mutableInvoiceCustomFields = new LinkedHashMap<String, Object>(resolvedInvoiceCustomFields);
    injectPromotedInvoiceAliases(mutableInvoiceCustomFields, invoice);
    invoiceMap.put("customFields", mutableInvoiceCustomFields);
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
                  lineMap.put("taxAmount", line.getTaxAmount());
                  lineMap.put("taxRateName", line.getTaxRateName());
                  BigDecimal lineTax =
                      line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO;
                  BigDecimal lineTotal = line.getAmount().add(lineTax);
                  lineMap.put("lineTotal", lineTotal);
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
              CustomerContextBuilder.populatePromotedCustomerFields(customerMap, customer);
              Map<String, Object> resolvedCustomerCustomFields =
                  contextHelper.resolveDropdownLabels(
                      customer.getCustomFields() != null ? customer.getCustomFields() : Map.of(),
                      EntityType.CUSTOMER,
                      fieldDefCache);
              var mutableCustomerCustomFields =
                  new LinkedHashMap<String, Object>(resolvedCustomerCustomFields);
              CustomerContextBuilder.injectPromotedCustomerAliases(
                  mutableCustomerCustomFields, customer);
              customerMap.put("customFields", mutableCustomerCustomFields);
              customerMap.put("address", invoice.getCustomerAddress());
              context.put("customer", customerMap);

              // Top-level convenience alias for customer VAT number.
              // Prefer the promoted structural column; fall back to JSONB for pre-Phase-63
              // entities. Legacy data may use either `vat_number` or `tax_number` as the JSONB
              // key — check both to maximise backward compatibility during the migration window.
              String vatNumber = customer.getTaxNumber();
              if (vatNumber == null) {
                var legacyVatNumber = mutableCustomerCustomFields.get("vat_number");
                var legacyTaxNumber = mutableCustomerCustomFields.get("tax_number");
                if (legacyVatNumber != null) {
                  vatNumber = legacyVatNumber.toString();
                } else if (legacyTaxNumber != null) {
                  vatNumber = legacyTaxNumber.toString();
                }
              }
              context.put("customerVatNumber", vatNumber);
            },
            () -> {
              context.put("customer", null);
              context.put("customerVatNumber", null);
            });

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
                          projectMap.put("referenceNumber", project.getReferenceNumber());
                          projectMap.put(
                              "priority",
                              project.getPriority() != null
                                  ? project.getPriority().name().toLowerCase(Locale.ROOT)
                                  : null);
                          projectMap.put("workType", project.getWorkType());
                          projectMap.put(
                              "customFields",
                              contextHelper.resolveDropdownLabels(
                                  project.getCustomFields() != null
                                      ? project.getCustomFields()
                                      : Map.of(),
                                  EntityType.PROJECT,
                                  fieldDefCache));
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

  /**
   * Injects backward-compatible {@code customFields.<slug>} aliases for promoted invoice fields.
   * {@code tax_type} is serialized lowercase to match the old pack dropdown values.
   */
  private static void injectPromotedInvoiceAliases(
      Map<String, Object> customFields, Invoice invoice) {
    if (invoice.getPoNumber() != null) {
      customFields.put("purchase_order_number", invoice.getPoNumber());
    }
    if (invoice.getTaxType() != null) {
      customFields.put("tax_type", invoice.getTaxType().name().toLowerCase(Locale.ROOT));
    }
    if (invoice.getBillingPeriodStart() != null) {
      customFields.put("billing_period_start", invoice.getBillingPeriodStart().toString());
    }
    if (invoice.getBillingPeriodEnd() != null) {
      customFields.put("billing_period_end", invoice.getBillingPeriodEnd().toString());
    }
  }
}
