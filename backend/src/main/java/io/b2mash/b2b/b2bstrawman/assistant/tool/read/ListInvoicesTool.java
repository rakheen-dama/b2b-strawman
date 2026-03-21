package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ListInvoicesTool implements AssistantTool {

  private final InvoiceService invoiceService;

  public ListInvoicesTool(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @Override
  public String name() {
    return "list_invoices";
  }

  @Override
  public String description() {
    return "List invoices for the current organization, with optional status and customer filters.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "status",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Filter by invoice status: DRAFT, APPROVED, SENT, PAID, or VOID"),
                "customerId", Map.of("type", "string", "description", "Filter by customer UUID")),
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
    var statusStr = (String) input.get("status");
    var customerIdStr = (String) input.get("customerId");

    InvoiceStatus status = null;
    if (statusStr != null && !statusStr.isBlank()) {
      try {
        status = InvoiceStatus.valueOf(statusStr.toUpperCase());
      } catch (IllegalArgumentException e) {
        return Map.of("error", "Invalid status value: " + statusStr);
      }
    }

    UUID customerId = null;
    if (customerIdStr != null && !customerIdStr.isBlank()) {
      try {
        customerId = UUID.fromString(customerIdStr);
      } catch (IllegalArgumentException e) {
        return Map.of("error", "Invalid customerId format: " + customerIdStr);
      }
    }

    var invoices = invoiceService.findAll(customerId, status, null);

    return invoices.stream()
        .map(
            inv -> {
              var map = new LinkedHashMap<String, Object>();
              map.put("id", inv.id().toString());
              map.put("invoiceNumber", inv.invoiceNumber());
              map.put("status", inv.status().name());
              map.put("customerId", inv.customerId() != null ? inv.customerId().toString() : null);
              map.put("customerName", inv.customerName());
              map.put("currency", inv.currency());
              map.put("total", inv.total());
              map.put("issueDate", inv.issueDate() != null ? inv.issueDate().toString() : null);
              map.put("dueDate", inv.dueDate() != null ? inv.dueDate().toString() : null);
              map.put("createdAt", inv.createdAt() != null ? inv.createdAt().toString() : null);
              return map;
            })
        .toList();
  }
}
