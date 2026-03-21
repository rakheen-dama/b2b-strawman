package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CreateCustomerTool implements AssistantTool {

  private final CustomerService customerService;

  public CreateCustomerTool(CustomerService customerService) {
    this.customerService = customerService;
  }

  @Override
  public String name() {
    return "create_customer";
  }

  @Override
  public String description() {
    return "Create a new customer (prospect) in the current organization.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "name", Map.of("type", "string", "description", "Customer name"),
                "email", Map.of("type", "string", "description", "Customer email address"),
                "phone", Map.of("type", "string", "description", "Customer phone number")),
        "required", List.of("name"));
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
    var name = (String) input.get("name");
    var email = (String) input.get("email");
    var phone = (String) input.get("phone");

    var customer =
        customerService.createCustomer(name, email, phone, null, null, context.memberId());

    var result = new LinkedHashMap<String, Object>();
    result.put("id", customer.getId().toString());
    result.put("name", customer.getName());
    result.put("status", customer.getLifecycleStatus().name());
    return result;
  }
}
