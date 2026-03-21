package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ListCustomersTool implements AssistantTool {

  private final CustomerService customerService;

  public ListCustomersTool(CustomerService customerService) {
    this.customerService = customerService;
  }

  @Override
  public String name() {
    return "list_customers";
  }

  @Override
  public String description() {
    return "List all customers for the current organization, with optional lifecycle status filter.";
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
                    "Filter by lifecycle status: PROSPECT, ONBOARDING, ACTIVE, DORMANT, OFFBOARDING, OFFBOARDED")),
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
    var statusFilter = (String) input.get("status");

    var customers =
        (statusFilter != null && !statusFilter.isBlank())
            ? customerService.listCustomersByLifecycleStatus(
                LifecycleStatus.valueOf(statusFilter.toUpperCase()))
            : customerService.listCustomers();

    return customers.stream()
        .map(
            c -> {
              var map = new LinkedHashMap<String, Object>();
              map.put("id", c.getId().toString());
              map.put("name", c.getName());
              map.put("status", c.getStatus());
              map.put("lifecycleStatus", c.getLifecycleStatus().name());
              map.put("email", c.getEmail());
              map.put("phone", c.getPhone());
              return map;
            })
        .toList();
  }
}
