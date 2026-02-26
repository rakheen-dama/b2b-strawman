package io.b2mash.b2b.b2bstrawman.integration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Component
public class IntegrationRegistry {

  // Built at startup: domain -> slug -> adapter bean
  private final Map<IntegrationDomain, Map<String, Object>> adapterMap = new ConcurrentHashMap<>();

  // Caffeine cache: "tenantSchema:DOMAIN" -> OrgIntegrationCacheEntry (never null)
  private final Cache<String, OrgIntegrationCacheEntry> configCache;

  private final OrgIntegrationRepository orgIntegrationRepository;

  public IntegrationRegistry(
      ApplicationContext applicationContext, OrgIntegrationRepository orgIntegrationRepository) {
    this.orgIntegrationRepository = orgIntegrationRepository;
    this.configCache =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60)).maximumSize(1000).build();

    // Scan for all beans with @IntegrationAdapter.
    // Fail fast if two adapters register with the same domain+slug combination.
    applicationContext
        .getBeansWithAnnotation(IntegrationAdapter.class)
        .forEach(
            (name, bean) -> {
              var annotation =
                  AnnotationUtils.findAnnotation(bean.getClass(), IntegrationAdapter.class);
              var slugMap =
                  adapterMap.computeIfAbsent(annotation.domain(), k -> new ConcurrentHashMap<>());
              var existing = slugMap.putIfAbsent(annotation.slug(), bean);
              if (existing != null) {
                throw new IllegalStateException(
                    "Duplicate @IntegrationAdapter: domain="
                        + annotation.domain()
                        + ", slug="
                        + annotation.slug()
                        + " registered by both "
                        + existing.getClass().getName()
                        + " and "
                        + bean.getClass().getName());
              }
            });
  }

  /**
   * Resolve the active adapter for the current tenant and domain. Returns the default adapter (as
   * defined by {@link IntegrationDomain#getDefaultSlug()}) if no integration is configured or
   * enabled.
   */
  @SuppressWarnings("unchecked")
  public <T> T resolve(IntegrationDomain domain, Class<T> portInterface) {
    if (!RequestScopes.TENANT_ID.isBound()) {
      throw new IllegalStateException("resolve() must be called within a tenant-scoped context");
    }
    var tenantSchema = RequestScopes.TENANT_ID.get();
    var cacheKey = tenantSchema + ":" + domain.name();

    // IMPORTANT: Caffeine cache.get(key, loader) throws NPE if loader returns null.
    // Always use .orElse(EMPTY) -- never return null from this lambda.
    var entry =
        configCache.get(
            cacheKey,
            k ->
                orgIntegrationRepository
                    .findByDomain(domain)
                    .map(OrgIntegrationCacheEntry::of)
                    .orElse(OrgIntegrationCacheEntry.EMPTY));

    var slugMap = adapterMap.getOrDefault(domain, Map.of());

    if (entry == OrgIntegrationCacheEntry.EMPTY || !entry.enabled()) {
      // Return default adapter for this domain
      var defaultAdapter = slugMap.get(domain.getDefaultSlug());
      if (defaultAdapter == null) {
        throw new IllegalStateException(
            "No " + domain.getDefaultSlug() + " adapter registered for domain " + domain);
      }
      return (T) defaultAdapter;
    }

    var adapter = slugMap.get(entry.providerSlug());
    if (adapter == null) {
      // Configured slug has no registered adapter -- fall back to default
      var defaultAdapter = slugMap.get(domain.getDefaultSlug());
      if (defaultAdapter == null) {
        throw new IllegalStateException(
            "No " + domain.getDefaultSlug() + " adapter registered for domain " + domain);
      }
      return (T) defaultAdapter;
    }

    if (!portInterface.isInstance(adapter)) {
      throw new IllegalStateException(
          "Adapter "
              + adapter.getClass().getName()
              + " does not implement "
              + portInterface.getName()
              + " for domain "
              + domain);
    }

    return (T) adapter;
  }

  /**
   * Resolves an adapter by domain and slug directly, bypassing tenant config lookup. Used by
   * webhook controllers that need to route to a specific adapter before the tenant context is
   * established.
   *
   * <p><strong>Security:</strong> The {@code slug} parameter must be validated and allowlisted by
   * the caller before invoking this method. Never pass a slug value sourced directly from an HTTP
   * request header or body without prior validation — the {@link IllegalArgumentException} thrown
   * for unknown slugs must not propagate verbatim to HTTP responses, as it may leak internal
   * adapter registration details.
   *
   * @throws IllegalArgumentException if no adapters are registered for the domain, or if no adapter
   *     is registered for the given slug.
   * @throws IllegalStateException if the found adapter does not implement the port interface.
   */
  @SuppressWarnings("unchecked")
  public <T> T resolveBySlug(IntegrationDomain domain, String slug, Class<T> portInterface) {
    var slugMap = adapterMap.get(domain);
    if (slugMap == null || slugMap.isEmpty()) {
      throw new IllegalArgumentException("No adapters registered for domain " + domain);
    }
    var adapter = slugMap.get(slug);
    if (adapter == null) {
      throw new IllegalArgumentException(
          "No adapter registered for domain=" + domain + ", slug=" + slug);
    }
    if (!portInterface.isInstance(adapter)) {
      throw new IllegalStateException(
          "Adapter "
              + adapter.getClass().getName()
              + " does not implement "
              + portInterface.getName()
              + " for domain "
              + domain);
    }
    return (T) adapter;
  }

  /** Lists available provider slugs for a given domain. */
  public List<String> availableProviders(IntegrationDomain domain) {
    return List.copyOf(adapterMap.getOrDefault(domain, Map.of()).keySet());
  }

  /** Evict cached config for a tenant + domain (called on config change). */
  public void evict(String tenantSchema, IntegrationDomain domain) {
    configCache.invalidate(tenantSchema + ":" + domain.name());
  }

  private record OrgIntegrationCacheEntry(String providerSlug, boolean enabled, String configJson) {
    static final OrgIntegrationCacheEntry EMPTY = new OrgIntegrationCacheEntry(null, false, null);

    static OrgIntegrationCacheEntry of(OrgIntegration integration) {
      return new OrgIntegrationCacheEntry(
          integration.getProviderSlug(), integration.isEnabled(), integration.getConfigJson());
    }
  }
}
