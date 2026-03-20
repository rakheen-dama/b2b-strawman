package io.b2mash.b2b.b2bstrawman.assistant.provider;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Auto-discovers all {@link LlmChatProvider} beans at startup and provides lookup by {@code
 * providerId()}. Fails fast on duplicate provider IDs.
 *
 * <p>Follows the same auto-discovery pattern as {@link
 * io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry} but with a simpler single-dimension
 * key (providerId instead of domain+slug).
 */
@Component
public class LlmChatProviderRegistry {

  private final Map<String, LlmChatProvider> providers;

  public LlmChatProviderRegistry(List<LlmChatProvider> chatProviders) {
    this.providers = new HashMap<>();
    for (var provider : chatProviders) {
      var existing = providers.putIfAbsent(provider.providerId(), provider);
      if (existing != null) {
        throw new IllegalStateException(
            "Duplicate LlmChatProvider providerId: \""
                + provider.providerId()
                + "\" registered by both "
                + existing.getClass().getName()
                + " and "
                + provider.getClass().getName());
      }
    }
  }

  /**
   * Returns the provider with the given ID.
   *
   * @throws IllegalArgumentException if no provider is registered with the given ID
   */
  public LlmChatProvider get(String providerId) {
    var provider = providers.get(providerId);
    if (provider == null) {
      throw new IllegalArgumentException(
          "No LlmChatProvider registered with providerId: \"" + providerId + "\"");
    }
    return provider;
  }

  /** Returns all registered providers. */
  public Collection<LlmChatProvider> getAll() {
    return List.copyOf(providers.values());
  }
}
