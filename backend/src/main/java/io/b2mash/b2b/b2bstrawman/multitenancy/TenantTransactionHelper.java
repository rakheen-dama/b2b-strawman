package io.b2mash.b2b.b2bstrawman.multitenancy;

import jakarta.persistence.EntityManagerFactory;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes a callback inside a tenant-scoped transaction with an explicitly set {@code
 * search_path}. This is necessary during tenant provisioning where Hibernate's {@link
 * SchemaMultiTenantConnectionProvider} may not be invoked for connections obtained via {@link
 * TransactionTemplate}, causing queries to run against the {@code public} schema instead of the
 * tenant schema.
 */
@Component
public class TenantTransactionHelper {

  private static final Logger log = LoggerFactory.getLogger(TenantTransactionHelper.class);
  private static final Pattern SCHEMA_PATTERN = Pattern.compile("^tenant_[0-9a-f]{12}$");

  private final TransactionTemplate transactionTemplate;
  private final EntityManagerFactory entityManagerFactory;

  public TenantTransactionHelper(
      TransactionTemplate transactionTemplate, EntityManagerFactory entityManagerFactory) {
    this.transactionTemplate = transactionTemplate;
    this.entityManagerFactory = entityManagerFactory;
  }

  /**
   * Binds tenant ScopedValues, starts a transaction, forces {@code search_path} to the tenant
   * schema, and runs the given action.
   *
   * @param tenantId tenant schema name (e.g., "tenant_a1b2c3d4e5f6")
   * @param orgId Clerk organization ID
   * @param action callback receiving the tenant ID for use in logging/seeding
   */
  public void executeInTenantTransaction(String tenantId, String orgId, Consumer<String> action) {
    validateSchema(tenantId);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, orgId)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      forceSearchPath(tenantId);
                      action.accept(tenantId);
                    }));
  }

  private void forceSearchPath(String schema) {
    var em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
    if (em == null) {
      throw new IllegalStateException(
          "No transactional EntityManager available — "
              + "forceSearchPath must be called inside a transaction");
    }
    em.createNativeQuery("SET search_path TO " + schema).executeUpdate();
    log.debug("Forced search_path to {} for tenant seeding", schema);
  }

  private void validateSchema(String schema) {
    if (!SCHEMA_PATTERN.matcher(schema).matches()) {
      throw new IllegalArgumentException("Invalid schema name: " + schema);
    }
  }
}
