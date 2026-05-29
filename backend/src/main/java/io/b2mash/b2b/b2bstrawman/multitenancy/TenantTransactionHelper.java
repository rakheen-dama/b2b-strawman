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
   * <p>The shard is inherited from the enclosing scope when one is bound (the normal case: this
   * runs inside {@code RequestScopes.runForTenantOnShard} during provisioning), otherwise it
   * defaults to the primary shard. Binding {@code SHARD_ID} explicitly closes the D4 gap where a
   * standalone caller — without an outer {@code runForTenantOnShard} — would leave it unbound and
   * silently route a secondary-shard tenant to the primary database. Use {@link
   * #executeInTenantTransaction(String, String, String, Consumer)} to pass the shard explicitly.
   *
   * @param tenantId tenant schema name (e.g., "tenant_a1b2c3d4e5f6")
   * @param orgId Clerk organization ID
   * @param action callback receiving the tenant ID for use in logging/seeding
   */
  public void executeInTenantTransaction(String tenantId, String orgId, Consumer<String> action) {
    String shardId =
        RequestScopes.SHARD_ID.isBound()
            ? RequestScopes.SHARD_ID.get()
            : ShardAndSchema.DEFAULT.shardId();
    executeInTenantTransaction(tenantId, orgId, shardId, action);
  }

  /**
   * Shard-explicit variant of {@link #executeInTenantTransaction(String, String, Consumer)}. Binds
   * {@code TENANT_ID}, {@code ORG_ID} and {@code SHARD_ID} so Hibernate routes to the correct
   * shard, then forces {@code search_path} and runs the action in a transaction.
   *
   * @param tenantId tenant schema name (e.g., "tenant_a1b2c3d4e5f6")
   * @param orgId Clerk organization ID
   * @param shardId shard identifier; null/blank defaults to the primary shard
   * @param action callback receiving the tenant ID for use in logging/seeding
   */
  public void executeInTenantTransaction(
      String tenantId, String orgId, String shardId, Consumer<String> action) {
    validateSchema(tenantId);
    String effectiveShardId =
        (shardId != null && !shardId.isBlank()) ? shardId : ShardAndSchema.DEFAULT.shardId();
    // Fail fast on a malformed shard id rather than late during identifier resolution.
    ShardAndSchema.requireValidShardId(effectiveShardId);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.SHARD_ID, effectiveShardId)
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
