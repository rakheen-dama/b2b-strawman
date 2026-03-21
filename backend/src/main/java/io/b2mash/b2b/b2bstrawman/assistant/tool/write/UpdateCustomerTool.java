package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UpdateCustomerTool implements AssistantTool {

  private final CustomerService customerService;

  public UpdateCustomerTool(CustomerService customerService) {
    this.customerService = customerService;
  }

  @Override
  public String name() {
    return "update_customer";
  }

  @Override
  public String description() {
    return "Update an existing customer's name, email, or phone number.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "customerId",
                    Map.of("type", "string", "description", "UUID of the customer to update"),
                "name", Map.of("type", "string", "description", "New customer name"),
                "email", Map.of("type", "string", "description", "New email address"),
                "phone", Map.of("type", "string", "description", "New phone number")),
        "required", List.of("customerId"));
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("CUSTOMER_MANAGEMENT");
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

    var name = (String) input.get("name");
    var email = (String) input.get("email");
    var phone = (String) input.get("phone");

    var customer = customerService.updateCustomer(customerId, name, email, phone, null, null);

    var result = new LinkedHashMap<String, Object>();
    result.put("id", customer.getId().toString());
    result.put("name", customer.getName());
    result.put("status", customer.getLifecycleStatus().name());
    result.put("email", customer.getEmail());
    result.put("phone", customer.getPhone());
    return result;
  }
}
