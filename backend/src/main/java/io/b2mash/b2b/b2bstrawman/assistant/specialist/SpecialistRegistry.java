package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Auto-discovers {@link Specialist} beans contributed by {@code *SpecialistConfig} classes and
 * exposes lookup + visibility filtering.
 *
 * <p>Authorization is purely capability-driven via {@code AI_ASSISTANT_USE}. This registry NEVER
 * references plan tiers — there are no plan-tier subscriptions in this product.
 */
@Component
public class SpecialistRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(SpecialistRegistry.class);
  private static final String CAPABILITY_AI_ASSISTANT_USE = "AI_ASSISTANT_USE";

  private final Map<String, Specialist> byId;
  private final List<Specialist> all;

  public SpecialistRegistry(List<Specialist> specialists) {
    var mutable = new HashMap<String, Specialist>();
    for (var s : specialists) {
      var existing = mutable.putIfAbsent(s.id(), s);
      if (existing != null) {
        throw new IllegalStateException(
            "Duplicate Specialist id: \"" + s.id() + "\" registered twice");
      }
    }
    this.byId = Map.copyOf(mutable);
    this.all = List.copyOf(mutable.values());
    LOG.info("Registered {} specialists", all.size());
  }

  /**
   * @throws ResourceNotFoundException if the id is unknown.
   */
  public Specialist requireById(String id) {
    var s = byId.get(id);
    if (s == null) {
      throw new ResourceNotFoundException("Specialist", id);
    }
    return s;
  }

  public Optional<Specialist> findById(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  public List<Specialist> all() {
    return all;
  }

  /**
   * Returns the specialists visible to a caller with the given capabilities and current route. A
   * specialist is visible iff:
   *
   * <ol>
   *   <li>the caller has the {@code AI_ASSISTANT_USE} capability, AND
   *   <li>the route matches at least one of the specialist's launcher routes (or {@code route} is
   *       null/blank — list all in that case), AND
   *   <li>the specialist exposes at least one tool the caller is permitted to use, OR the
   *       specialist declares no tool requirements.
   * </ol>
   */
  public List<Specialist> visibleTo(Set<String> capabilities, String route) {
    if (capabilities == null || !capabilities.contains(CAPABILITY_AI_ASSISTANT_USE)) {
      return List.of();
    }
    return all.stream().filter(s -> matchesRoute(s, route)).toList();
  }

  private static boolean matchesRoute(Specialist s, String route) {
    if (route == null || route.isBlank()) {
      return true;
    }
    return s.launchers().stream().anyMatch(l -> routePrefixMatches(l.route(), route));
  }

  private static boolean routePrefixMatches(String launcherRoute, String currentRoute) {
    if (launcherRoute == null || launcherRoute.isBlank()) {
      return true;
    }
    return currentRoute.startsWith(launcherRoute);
  }
}
