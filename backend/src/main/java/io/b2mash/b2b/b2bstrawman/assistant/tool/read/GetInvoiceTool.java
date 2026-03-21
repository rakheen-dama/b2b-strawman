package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetInvoiceTool implements AssistantTool {

  private final InvoiceService invoiceService;

  public GetInvoiceTool(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @Override
  public String name() {
    return "get_invoice";
  }

  @Override
  public String description() {
    return "Get full invoice details including line items, by invoice ID or invoice number.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "invoiceId", Map.of("type", "string", "description", "UUID of the invoice"),
                "invoiceNumber",
                    Map.of(
                        "type", "string", "description", "Invoice number string (e.g., INV-0042)")),
        "required", List.of());
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("INVOICING");
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var invoiceIdStr = (String) input.get("invoiceId");
    var invoiceNumber = (String) input.get("invoiceNumber");

    if ((invoiceIdStr == null || invoiceIdStr.isBlank())
        && (invoiceNumber == null || invoiceNumber.isBlank())) {
      return Map.of("error", "Either invoiceId or invoiceNumber is required");
    }

    InvoiceResponse invoice;

    if (invoiceIdStr != null && !invoiceIdStr.isBlank()) {
      UUID invoiceId;
      try {
        invoiceId = UUID.fromString(invoiceIdStr);
      } catch (IllegalArgumentException e) {
        return Map.of("error", "Invalid invoiceId format: " + invoiceIdStr);
      }

      try {
        invoice = invoiceService.findById(invoiceId);
      } catch (ResourceNotFoundException e) {
        return Map.of("error", "Invoice not found");
      }
    } else {
      // Look up by invoice number
      try {
        invoice = invoiceService.findByInvoiceNumber(invoiceNumber);
      } catch (ResourceNotFoundException e) {
        return Map.of("error", "Invoice not found");
      }
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("id", invoice.id().toString());
    result.put("invoiceNumber", invoice.invoiceNumber());
    result.put("status", invoice.status().name());
    result.put("customerId", invoice.customerId() != null ? invoice.customerId().toString() : null);
    result.put("customerName", invoice.customerName());
    result.put("currency", invoice.currency());
    result.put("subtotal", invoice.subtotal());
    result.put("taxAmount", invoice.taxAmount());
    result.put("total", invoice.total());
    result.put("issueDate", invoice.issueDate() != null ? invoice.issueDate().toString() : null);
    result.put("dueDate", invoice.dueDate() != null ? invoice.dueDate().toString() : null);
    result.put("paymentTerms", invoice.paymentTerms());
    result.put("notes", invoice.notes());
    result.put("createdAt", invoice.createdAt() != null ? invoice.createdAt().toString() : null);
    result.put(
        "lines",
        invoice.lines().stream()
            .map(
                line -> {
                  var lineMap = new LinkedHashMap<String, Object>();
                  lineMap.put("id", line.id().toString());
                  lineMap.put("description", line.description());
                  lineMap.put("quantity", line.quantity());
                  lineMap.put("unitPrice", line.unitPrice());
                  lineMap.put("amount", line.amount());
                  lineMap.put("lineType", line.lineType() != null ? line.lineType().name() : null);
                  lineMap.put("projectName", line.projectName());
                  return lineMap;
                })
            .toList());
    return result;
  }
}
