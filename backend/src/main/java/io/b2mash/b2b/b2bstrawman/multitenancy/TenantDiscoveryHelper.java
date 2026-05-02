package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sanctioned API for cross-tenant <em>discovery</em> searches — iterate every active tenant schema
 * and return the first match for which {@code finder} returns a non-empty {@link Optional}.
 *
 * <p>Use for resolving an opaque token (acceptance request token, payment session id, etc.) to its
 * owning tenant when the token format does not embed the tenant. For per-tenant fan-out where the
 * action runs on every tenant (regardless of result), use {@link TenantScopedRunner#forEachTenant}
 * instead.
 *
 * <p>Per-tenant exception isolation: failures inside {@code finder} for one tenant are caught,
 * logged at WARN with {@code tenantId} in the message, and do NOT abort the iteration. The next
 * tenant is tried. Returns {@link Optional#empty()} if no tenant matches and no tenant succeeded.
 *
 * <p>The returned {@link TenantMatch} carries the matching tenant's identifiers ({@code tenantId},
 * {@code orgId}) alongside the value the finder returned, so callers can construct domain-specific
 * context records (e.g. {@code TenantAcceptanceContext}) without re-querying the mapping.
 */
@Component
public class TenantDiscoveryHelper {

  private static final Logger log = LoggerFactory.getLogger(TenantDiscoveryHelper.class);

  private final OrgSchemaMappingRepository mappingRepository;

  public TenantDiscoveryHelper(OrgSchemaMappingRepository mappingRepository) {
    this.mappingRepository = mappingRepository;
  }

  /**
   * Iterate active tenant schemas; return the first match.
   *
   * @param finder invoked once per tenant with {@link RequestScopes#TENANT_ID} (and {@link
   *     RequestScopes#ORG_ID}) bound. Returning a non-empty {@link Optional} stops iteration.
   * @return Optional containing the first match, or empty if no tenant matches.
   * @throws NullPointerException if {@code finder} is null.
   */
  public <T> Optional<TenantMatch<T>> findInTenants(Supplier<Optional<T>> finder) {
    Objects.requireNonNull(finder, "finder");
    for (var mapping : mappingRepository.findAll()) {
      String tenantId = mapping.getSchemaName();
      String orgId = mapping.getExternalOrgId();
      try {
        Optional<T> result = RequestScopes.callForTenant(tenantId, orgId, finder::get);
        if (result.isPresent()) {
          return Optional.of(new TenantMatch<>(tenantId, orgId, result.get()));
        }
      } catch (RuntimeException e) {
        log.warn("Tenant discovery error in schema {}: {}", tenantId, e.getMessage(), e);
      }
    }
    return Optional.empty();
  }

  /** A single tenant's match — the bound identifiers plus the discovered value. */
  public record TenantMatch<T>(String tenantId, String orgId, T value) {}
}
