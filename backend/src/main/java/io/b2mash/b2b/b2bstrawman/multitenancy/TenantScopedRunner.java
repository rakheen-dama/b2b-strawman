package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.util.Objects;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Iterates every active tenant schema and runs an action once per tenant with TENANT_ID + ORG_ID
 * bound on a fresh ScopedValue carrier. The canonical replacement for the inline {@code for
 * (mapping : repo.findAll()) { ScopedValue.where(...).run(...) }} pattern that appeared in 13+
 * scheduled jobs prior to PR #2 (ADR-T008 Surface 2).
 *
 * <p>Per-tenant exception isolation: failures inside {@code action} for one tenant are caught,
 * logged at ERROR with {@code tenantId} / {@code orgId} in the message, and do NOT abort the
 * iteration. Returns the count of tenants for which {@code action} completed without throwing.
 */
@Component
public class TenantScopedRunner {

  private static final Logger log = LoggerFactory.getLogger(TenantScopedRunner.class);

  private final OrgSchemaMappingRepository mappingRepository;

  public TenantScopedRunner(OrgSchemaMappingRepository mappingRepository) {
    this.mappingRepository = mappingRepository;
  }

  /**
   * Run {@code action} once per tenant schema with {@link RequestScopes#TENANT_ID} and {@link
   * RequestScopes#ORG_ID} bound. The action receives {@code (tenantId, orgId)}.
   *
   * @return the count of tenants for which {@code action} completed without throwing.
   * @throws NullPointerException if {@code action} is null.
   */
  public int forEachTenant(BiConsumer<String, String> action) {
    Objects.requireNonNull(action, "action");
    int succeeded = 0;
    for (var mapping : mappingRepository.findAll()) {
      String tenantId = mapping.getSchemaName();
      String orgId = mapping.getExternalOrgId();
      String shardId = mapping.getShardId();
      try {
        RequestScopes.runForTenantOnShard(
            tenantId, orgId, shardId, () -> action.accept(tenantId, orgId));
        succeeded++;
      } catch (Exception e) {
        log.error(
            "Per-tenant action failed: tenant={} org={}: {}", tenantId, orgId, e.getMessage(), e);
      }
    }
    return succeeded;
  }
}
