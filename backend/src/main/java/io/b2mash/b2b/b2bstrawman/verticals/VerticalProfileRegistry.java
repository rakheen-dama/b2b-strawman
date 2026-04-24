package io.b2mash.b2b.b2bstrawman.verticals;

import java.io.IOException;
import java.math.BigDecimal;
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

  /**
   * Role-keyed rate (e.g. {@code Owner R1800}). The {@code roleName} is matched against an {@link
   * io.b2mash.b2b.b2bstrawman.orgrole.OrgRole} slug case-insensitively.
   */
  public record RoleRate(String roleName, BigDecimal hourlyRate) {}

  /**
   * Rate card defaults block parsed from a vertical profile JSON. Supplies role-keyed billing and
   * cost rates used to seed {@code MEMBER_DEFAULT} rates on JIT member creation.
   */
  public record RateCardDefaults(
      String currency, List<RoleRate> billingRates, List<RoleRate> costRates) {}

  /**
   * Default tax rate entry parsed from a vertical profile's {@code taxDefaults} array. The {@code
   * name} is treated as the jurisdiction-appropriate rate label (e.g. {@code "VAT"}); the
   * reconciliation seeder prefixes it onto the canonical tier name (e.g. {@code "VAT — Standard"}).
   */
  public record TaxDefault(String name, BigDecimal rate, boolean isDefault) {}

  /** Immutable definition of a vertical profile loaded from a JSON file. */
  public record ProfileDefinition(
      String profileId,
      String name,
      String description,
      List<String> enabledModules,
      String terminologyNamespace,
      String currency,
      Map<String, Object> packs,
      RateCardDefaults rateCardDefaults,
      List<TaxDefault> taxDefaults) {}

  private final Map<String, ProfileDefinition> profiles;

  public VerticalProfileRegistry(ObjectMapper objectMapper) throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath:vertical-profiles/*.json");

    Map<String, ProfileDefinition> loaded = new LinkedHashMap<>();
    for (Resource resource : resources) {
      try (var is = resource.getInputStream()) {
        JsonNode root = objectMapper.readTree(is);

        String profileId = root.path("profileId").asText(null);
        if (profileId == null || profileId.isBlank()) {
          log.warn(
              "Skipping vertical profile file {} — missing or blank profileId",
              resource.getFilename());
          continue;
        }

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

        RateCardDefaults rateCardDefaults = parseRateCardDefaults(root.path("rateCardDefaults"));
        List<TaxDefault> taxDefaults = parseTaxDefaults(root.path("taxDefaults"));

        var profile =
            new ProfileDefinition(
                profileId,
                name,
                description,
                enabledModules,
                terminologyNamespace,
                currency,
                packs,
                rateCardDefaults,
                taxDefaults);
        loaded.put(profileId, profile);

        log.info("Loaded vertical profile: {}", profileId);
      } catch (IOException e) {
        log.warn(
            "Skipping malformed vertical profile file {} — {}",
            resource.getFilename(),
            e.getMessage());
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

  /**
   * Parses the {@code rateCardDefaults} block from a profile JSON node. Returns {@code null} if the
   * node is missing or malformed (seeding is simply skipped in that case).
   */
  private static RateCardDefaults parseRateCardDefaults(JsonNode node) {
    if (node == null || node.isMissingNode() || !node.isObject()) {
      return null;
    }
    String currency = node.path("currency").asText(null);
    List<RoleRate> billing = parseRoleRateArray(node.path("billingRates"));
    List<RoleRate> cost = parseRoleRateArray(node.path("costRates"));
    if (currency == null && billing.isEmpty() && cost.isEmpty()) {
      return null;
    }
    return new RateCardDefaults(currency, billing, cost);
  }

  /**
   * Parses the {@code taxDefaults} array from a profile JSON node. Returns an empty list if the
   * node is missing, not an array, or contains no parseable entries — reconciliation simply skips
   * tax-default seeding in that case.
   */
  private static List<TaxDefault> parseTaxDefaults(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<TaxDefault> out = new ArrayList<>();
    for (JsonNode entry : node) {
      String name = entry.path("name").asText(null);
      JsonNode rateNode = entry.path("rate");
      if (name == null || name.isBlank() || rateNode.isMissingNode() || rateNode.isNull()) {
        continue;
      }
      BigDecimal rate;
      try {
        rate = new BigDecimal(rateNode.asText());
      } catch (NumberFormatException e) {
        continue;
      }
      boolean isDefault = entry.path("default").asBoolean(false);
      out.add(new TaxDefault(name, rate, isDefault));
    }
    return List.copyOf(out);
  }

  private static List<RoleRate> parseRoleRateArray(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray()) {
      return List.of();
    }
    List<RoleRate> out = new ArrayList<>();
    for (JsonNode entry : arrayNode) {
      String roleName = entry.path("roleName").asText(null);
      JsonNode rateNode = entry.path("hourlyRate");
      if (roleName == null || roleName.isBlank() || rateNode.isMissingNode() || rateNode.isNull()) {
        continue;
      }
      BigDecimal hourlyRate;
      try {
        hourlyRate = new BigDecimal(rateNode.asText());
      } catch (NumberFormatException e) {
        continue;
      }
      out.add(new RoleRate(roleName, hourlyRate));
    }
    return List.copyOf(out);
  }
}
