package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CreateInvoiceDraftTool implements AssistantTool {

  private final InvoiceService invoiceService;
  private final OrgSettingsService orgSettingsService;

  public CreateInvoiceDraftTool(
      InvoiceService invoiceService, OrgSettingsService orgSettingsService) {
    this.invoiceService = invoiceService;
    this.orgSettingsService = orgSettingsService;
  }

  @Override
  public String name() {
    return "create_invoice_draft";
  }

  @Override
  public String description() {
    return "Create a draft invoice for a customer, optionally including unbilled time entries.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "customerId", Map.of("type", "string", "description", "UUID of the customer"),
                "includeUnbilledTime",
                    Map.of(
                        "type",
                        "boolean",
                        "description",
                        "Whether to include unbilled time entries as line items")),
        "required", List.of("customerId"));
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("INVOICING");
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var customerIdStr = (String) input.get("customerId");
    UUID customerId;
    try {
      customerId = UUID.fromString(customerIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid customerId format: " + customerIdStr);
    }

    var includeUnbilledTime = Boolean.TRUE.equals(input.get("includeUnbilledTime"));
    var currency = orgSettingsService.getDefaultCurrency();

    List<UUID> timeEntryIds = null;
    if (includeUnbilledTime) {
      var unbilled = invoiceService.getUnbilledTime(customerId, null, null);
      timeEntryIds =
          unbilled.projects().stream()
              .flatMap(group -> group.entries().stream())
              .map(entry -> entry.id())
              .toList();
      if (timeEntryIds.isEmpty()) {
        timeEntryIds = null;
      }
    }

    var request =
        new CreateInvoiceRequest(
            customerId, currency, timeEntryIds, null, null, null, null, null, null, null, null);
    var invoiceResponse = invoiceService.createDraft(request, context.memberId());

    var result = new LinkedHashMap<String, Object>();
    result.put("id", invoiceResponse.id().toString());
    result.put("invoiceNumber", invoiceResponse.invoiceNumber());
    result.put("status", invoiceResponse.status().name());
    result.put("totalAmount", invoiceResponse.total().toString());
    return result;
  }
}
