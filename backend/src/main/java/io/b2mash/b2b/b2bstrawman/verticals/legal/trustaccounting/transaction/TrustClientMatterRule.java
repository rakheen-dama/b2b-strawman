package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * G89-F28 (D36): every new client trust posting names a matter linked to the client (directly,
 * {@code projects.customer_id}, or through {@code customer_projects} — the client's matter list). A
 * row without a matter never reaches the portal, whose trust view is per client and matter, so the
 * client's portal balance would fall short of the firm ledger. Rows derived from an existing row
 * (reversals) do not pass through here.
 *
 * <p>Shared by {@link TrustTransactionService} and, from 647D, the allocation of an unidentified
 * deposit ({@code creditor.UnidentifiedDepositService}) — one copy of the rule, one refusal shape
 * ({@link #MATTER_REQUIRED}, {@link #MATTER_NOT_LINKED_TO_CLIENT}).
 */
@Component
public class TrustClientMatterRule {

  /** ProblemDetail {@code code} of a client posting refused for want of a matter (G89-F28). */
  public static final String MATTER_REQUIRED = "MATTER_REQUIRED";

  /** ProblemDetail {@code code} of a client posting on a matter not linked to it (G89-F28). */
  public static final String MATTER_NOT_LINKED_TO_CLIENT = "MATTER_NOT_LINKED_TO_CLIENT";

  /** The refusal detail for a client trust posting without a matter (G89-F28, D36). */
  static final String MATTER_REQUIRED_DETAIL = "A matter is required for client trust transactions";

  private final ProjectRepository projectRepository;
  private final CustomerProjectRepository customerProjectRepository;

  public TrustClientMatterRule(
      ProjectRepository projectRepository, CustomerProjectRepository customerProjectRepository) {
    this.projectRepository = projectRepository;
    this.customerProjectRepository = customerProjectRepository;
  }

  /**
   * Refuses (400) a null matter ("Matter required") or a matter not linked to {@code client}
   * ("Matter not linked to client"); 404 for an unknown matter. Returns the matter, loaded once.
   */
  public Project requireClientMatter(Customer client, UUID matterId) {
    if (matterId == null) {
      throw matterRequired(null);
    }
    var matter =
        projectRepository
            .findById(matterId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, matterId));
    if (client.getId().equals(matter.getCustomerId())
        || customerProjectRepository.existsByCustomerIdAndProjectId(client.getId(), matterId)) {
      return matter;
    }
    var properties = new LinkedHashMap<String, Object>();
    properties.put("code", MATTER_NOT_LINKED_TO_CLIENT);
    properties.put("matterId", matterId);
    throw InvalidStateException.withProperties(
        "Matter not linked to client",
        "Matter " + matter.getName() + " is not linked to client " + client.getName(),
        properties);
  }

  /** The "Matter required" refusal, with an optional {@code reason} property. */
  static InvalidStateException matterRequired(String reason) {
    var properties = new LinkedHashMap<String, Object>();
    properties.put("code", MATTER_REQUIRED);
    if (reason != null) {
      properties.put("reason", reason);
    }
    return InvalidStateException.withProperties(
        "Matter required", MATTER_REQUIRED_DETAIL, properties);
  }
}
