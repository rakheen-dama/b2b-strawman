package io.b2mash.b2b.b2bstrawman.project;

import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Resolves a project name from a naming pattern by replacing placeholder tokens with actual values.
 * Supported placeholders: {name}, {customer.name}, and any custom field slug (e.g.,
 * {reference_number}).
 */
@Service
public class ProjectNameResolver {

  /**
   * Resolves a naming pattern into a concrete project name. If the pattern is null or blank,
   * returns the original project name unchanged.
   *
   * @param pattern the naming pattern (e.g., "{reference_number} - {customer.name} - {name}")
   * @param projectName the original project name
   * @param customFields custom field values keyed by slug
   * @param customerName the associated customer name, or null
   * @return the resolved project name
   */
  public String resolve(
      String pattern, String projectName, Map<String, Object> customFields, String customerName) {
    if (pattern == null || pattern.isBlank()) {
      return projectName;
    }

    String result = pattern;
    result = result.replace("{name}", projectName != null ? projectName : "");
    result = result.replace("{customer.name}", customerName != null ? customerName : "");

    // Replace custom field references
    if (customFields != null) {
      for (var entry : customFields.entrySet()) {
        result =
            result.replace(
                "{" + entry.getKey() + "}",
                entry.getValue() != null ? String.valueOf(entry.getValue()) : "");
      }
    }

    // Clean trailing separators (e.g., " - " at end when customer.name is empty)
    return result.trim().replaceAll("\\s+-\\s*$", "").trim();
  }
}
