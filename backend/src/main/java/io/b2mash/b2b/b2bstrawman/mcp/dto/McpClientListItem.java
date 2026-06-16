package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import java.util.UUID;

/**
 * Compact {@code list_clients} row (§11.4): {@code {id, name, type, lifecycleStatus}} only.
 *
 * <p>Decision: deliberately OMITs the controller's tag/member-name enrichment ({@code
 * CustomerResponse.from}) — keeping the list token-efficient and avoiding N extra batch queries.
 * Full client detail (contacts, linked matters) is fetched on demand by {@code get_client}.
 *
 * @param id client id
 * @param name client name
 * @param type customer type short enum name (e.g. {@code COMPANY})
 * @param lifecycleStatus lifecycle short enum name (e.g. {@code ACTIVE})
 */
public record McpClientListItem(UUID id, String name, String type, String lifecycleStatus) {

  public static McpClientListItem from(Customer customer) {
    return new McpClientListItem(
        customer.getId(),
        customer.getName(),
        customer.getCustomerType() != null ? customer.getCustomerType().name() : null,
        customer.getLifecycleStatus() != null ? customer.getLifecycleStatus().name() : null);
  }
}
