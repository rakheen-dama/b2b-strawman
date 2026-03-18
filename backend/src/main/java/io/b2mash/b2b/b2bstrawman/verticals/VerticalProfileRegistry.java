package io.b2mash.b2b.b2bstrawman.verticals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Registry that loads vertical profile definitions from classpath JSON files at startup. Profiles
 * are read from {@code classpath:vertical-profiles/*.json}.
 */
@Component
public class VerticalProfileRegistry {

  private static final Logger log = LoggerFactory.getLogger(VerticalProfileRegistry.class);

  /** Immutable definition of a vertical profile loaded from a JSON file. */
  public record ProfileDefinition(
      String profileId,
      String name,
      String description,
      List<String> enabledModules,
      String terminologyNamespace,
      String currency,
      Map<String, Object> packs) {}

  private final Map<String, ProfileDefinition> profiles;

  public VerticalProfileRegistry(ObjectMapper objectMapper) throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath:vertical-profiles/*.json");

    Map<String, ProfileDefinition> loaded = new LinkedHashMap<>();
    for (Resource resource : resources) {
      try (var is = resource.getInputStream()) {
        JsonNode root = objectMapper.readTree(is);

        String profileId = root.path("profileId").asText();
        String name = root.path("name").asText();
        String description = root.path("description").asText();
        String currency = root.path("currency").asText(null);

        // enabledModules may be absent — default to empty list
        List<String> enabledModules;
        if (root.has("enabledModules") && root.get("enabledModules").isArray()) {
          List<String> modules = new ArrayList<>();
          for (JsonNode node : root.get("enabledModules")) {
            modules.add(node.asText());
          }
          enabledModules = List.copyOf(modules);
        } else {
          enabledModules = List.of();
        }

        // terminologyOverrides in JSON maps to terminologyNamespace
        String terminologyNamespace = root.path("terminologyOverrides").asText(null);

        // packs is a heterogeneous map
        @SuppressWarnings("unchecked")
        Map<String, Object> packs =
            root.has("packs") ? objectMapper.convertValue(root.get("packs"), Map.class) : Map.of();

        var profile =
            new ProfileDefinition(
                profileId,
                name,
                description,
                enabledModules,
                terminologyNamespace,
                currency,
                packs);
        loaded.put(profileId, profile);

        log.info("Loaded vertical profile: {}", profileId);
      }
    }

    this.profiles = Collections.unmodifiableMap(loaded);
    log.info("Loaded {} vertical profile(s)", profiles.size());
  }

  /** Returns the profile definition for the given ID, or empty if not found. */
  public Optional<ProfileDefinition> getProfile(String id) {
    return Optional.ofNullable(profiles.get(id));
  }

  /** Returns all loaded profile definitions. */
  public List<ProfileDefinition> getAllProfiles() {
    return List.copyOf(profiles.values());
  }

  /** Returns true if a profile with the given ID exists. */
  public boolean exists(String id) {
    return profiles.containsKey(id);
  }
}
