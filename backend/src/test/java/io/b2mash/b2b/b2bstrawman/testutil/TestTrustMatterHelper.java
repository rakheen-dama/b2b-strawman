package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F28 (D36): every client trust posting (deposit, payment, refund, transfer, investment) names
 * a matter linked to the client. Trust tests that only need "a matter of this client" open one
 * here: the matter is saved with {@code projects.customer_id} = the client (the direct link the
 * trust guard accepts), straight through the repository so it works for clients in any lifecycle
 * status (the API's CREATE_PROJECT gate refuses PROSPECT clients).
 */
public final class TestTrustMatterHelper {

  private TestTrustMatterHelper() {}

  /** Opens a matter for {@code clientId} in {@code tenantSchema} and returns its id. */
  public static UUID openClientMatter(
      String tenantSchema,
      TransactionTemplate transactionTemplate,
      ProjectRepository projectRepository,
      UUID clientId,
      String name,
      UUID createdBy) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var matter = new Project(name, "Trust test matter", createdBy);
                      matter.setCustomerId(clientId);
                      return projectRepository.saveAndFlush(matter).getId();
                    }));
  }

  /** As above, with the client's creator as the matter's creator. */
  public static UUID openClientMatter(
      String tenantSchema,
      TransactionTemplate transactionTemplate,
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      UUID clientId,
      String name) {
    UUID createdBy =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx -> customerRepository.findById(clientId).orElseThrow().getCreatedBy()));
    return openClientMatter(
        tenantSchema, transactionTemplate, projectRepository, clientId, name, createdBy);
  }

  /** Links an existing matter to the client through {@code customer_projects} (idempotent). */
  public static void linkClientMatter(
      String tenantSchema,
      TransactionTemplate transactionTemplate,
      CustomerProjectRepository customerProjectRepository,
      UUID clientId,
      UUID matterId,
      UUID createdBy) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      if (!customerProjectRepository.existsByCustomerIdAndProjectId(
                          clientId, matterId)) {
                        customerProjectRepository.saveAndFlush(
                            new CustomerProject(clientId, matterId, createdBy));
                      }
                    }));
  }

  /**
   * Turns a posting the service just recorded into the legacy client-level shape written before
   * G89-F28: {@code project_id} null, every other effect (ledger card, status) as recorded. D6:
   * such rows are not backfilled, so tests of the legacy (unmattered) paths build them this way.
   */
  public static void stripMatter(
      String tenantSchema,
      TransactionTemplate transactionTemplate,
      EntityManager entityManager,
      UUID transactionId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx ->
                        entityManager
                            .createNativeQuery(
                                "UPDATE trust_transactions SET project_id = NULL WHERE id = :id")
                            .setParameter("id", transactionId)
                            .executeUpdate()));
  }
}
