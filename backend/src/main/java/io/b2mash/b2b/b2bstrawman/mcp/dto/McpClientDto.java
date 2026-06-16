package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.project.Project;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed client projection for {@code get_client} and the {@code kazi://client/{id}} resource
 * (§11.4): {@code {id, name, type, lifecycleStatus, contacts[], linkedMatters[]}}.
 *
 * <p>DRIFT note (vs §11.4): {@code Customer} has no first-class {@code contacts[]} collection — it
 * carries single {@code contactName/contactEmail/contactPhone} fields. We project those as a
 * single-element {@code contacts} list (or an empty list if all three are null). {@code
 * linkedMatters} is resolved from a SEPARATE {@code CustomerProjectService.listProjectsForCustomer}
 * call (NOT from {@code getCustomer}), mirroring the controller's {@code LinkedProjectResponse}.
 */
public record McpClientDto(
    UUID id,
    String name,
    String type,
    String lifecycleStatus,
    List<Contact> contacts,
    List<LinkedMatter> linkedMatters) {

  /**
   * Single client contact (projected from the flat {@code contact*} fields on {@code Customer}).
   */
  public record Contact(String name, String email, String phone) {}

  /** Compact reference to a matter linked to this client. */
  public record LinkedMatter(UUID id, String name, Instant createdAt) {}

  /**
   * Projects a {@link Customer} entity plus its separately-resolved linked {@link Project}s into
   * the MCP DTO.
   */
  public static McpClientDto from(Customer customer, List<Project> linkedProjects) {
    List<Contact> contacts =
        (customer.getContactName() == null
                && customer.getContactEmail() == null
                && customer.getContactPhone() == null)
            ? List.of()
            : List.of(
                new Contact(
                    customer.getContactName(),
                    customer.getContactEmail(),
                    customer.getContactPhone()));

    List<LinkedMatter> linkedMatters =
        linkedProjects.stream()
            .map(p -> new LinkedMatter(p.getId(), p.getName(), p.getCreatedAt()))
            .toList();

    return new McpClientDto(
        customer.getId(),
        customer.getName(),
        customer.getCustomerType() != null ? customer.getCustomerType().name() : null,
        customer.getLifecycleStatus() != null ? customer.getLifecycleStatus().name() : null,
        contacts,
        linkedMatters);
  }
}
