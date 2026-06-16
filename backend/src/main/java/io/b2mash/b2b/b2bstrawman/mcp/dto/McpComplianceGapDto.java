package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.ChecklistInstanceResponse;
import java.util.List;
import java.util.UUID;

/**
 * Compliance-gap projection for {@code list_compliance_gaps} (Epic 564B): the client's FICA status
 * plus the flattened checklist items (name/status/required) across all checklist instances. Items
 * are capped at the MCP page max and {@code truncated} is set when the flattened item count
 * exceeded the cap.
 *
 * @param customerId the client
 * @param ficaStatus FICA verification status (NOT_STARTED / IN_PROGRESS / DONE)
 * @param items flattened checklist items, capped
 * @param truncated true when items were clipped to the cap
 */
public record McpComplianceGapDto(
    UUID customerId, String ficaStatus, List<Item> items, boolean truncated) {

  /** One flattened checklist item. */
  public record Item(String name, String status, boolean required) {}

  /**
   * Flattens the per-customer checklist instances into a capped item list.
   *
   * @param customerId the client
   * @param ficaStatus FICA status string
   * @param instances all checklist instances (with items) for the customer
   * @param maxItems item cap
   */
  public static McpComplianceGapDto from(
      UUID customerId, String ficaStatus, List<ChecklistInstanceResponse> instances, int maxItems) {
    List<Item> all =
        instances.stream()
            .filter(i -> i.items() != null)
            .flatMap(i -> i.items().stream())
            .map(it -> new Item(it.name(), it.status(), it.required()))
            .toList();
    boolean truncated = all.size() > maxItems;
    List<Item> capped = truncated ? all.subList(0, maxItems) : all;
    return new McpComplianceGapDto(customerId, ficaStatus, List.copyOf(capped), truncated);
  }
}
