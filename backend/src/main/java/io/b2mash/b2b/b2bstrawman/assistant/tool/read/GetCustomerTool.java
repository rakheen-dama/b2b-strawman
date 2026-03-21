package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetCustomerTool implements AssistantTool {

  private final CustomerService customerService;

  public GetCustomerTool(CustomerService customerService) {
    this.customerService = customerService;
  }

  @Override
  public String name() {
    return "get_customer";
  }

  @Override
  public String description() {
    return "Get detailed information about a specific customer by ID or name.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "customerId", Map.of("type", "string", "description", "UUID of the customer"),
                "customerName",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Name of the customer (used if customerId is not provided)")),
        "required", List.of());
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of();
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var customerId = (String) input.get("customerId");
    var customerName = (String) input.get("customerName");

    Customer customer;
    if (customerId != null && !customerId.isBlank()) {
      customer = customerService.getCustomer(UUID.fromString(customerId));
    } else if (customerName != null && !customerName.isBlank()) {
      customer =
          customerService.listCustomers().stream()
              .filter(c -> c.getName().equalsIgnoreCase(customerName))
              .findFirst()
              .orElse(null);
    } else {
      return Map.of("error", "Either customerId or customerName is required");
    }

    if (customer == null) {
      return Map.of("error", "Customer not found");
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("id", customer.getId().toString());
    result.put("name", customer.getName());
    result.put("status", customer.getStatus());
    result.put("lifecycleStatus", customer.getLifecycleStatus().name());
    result.put("email", customer.getEmail());
    result.put("phone", customer.getPhone());
    result.put("idNumber", customer.getIdNumber());
    result.put("notes", customer.getNotes());
    result.put("createdAt", customer.getCreatedAt().toString());
    return result;
  }
}
