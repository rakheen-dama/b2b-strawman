package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Walks a Tiptap JSON tree and extracts variable keys. Also resolves clauseBlock nodes by looking
 * up clause bodies and walking them recursively.
 */
@Component
public class TemplateVariableAnalyzer {

  private static final int MAX_CLAUSE_DEPTH = 10;

  private final ClauseRepository clauseRepository;

  public TemplateVariableAnalyzer(ClauseRepository clauseRepository) {
    this.clauseRepository = clauseRepository;
  }

  /**
   * Extracts all variable keys from a Tiptap document tree.
   *
   * @param document the root Tiptap JSON node (type: "doc")
   * @return a set of variable keys (e.g., "customer.name", "project.customFields.tax_number")
   */
  public Set<String> extractVariableKeys(Map<String, Object> document) {
    var keys = new HashSet<String>();
    if (document == null) return keys;
    walkNode(document, keys, 0);
    return keys;
  }

  /**
   * Extracts custom field slugs grouped by entity type prefix.
   *
   * <p>Pattern: {@code {entity}.customFields.{slug}} where entity is the lowercase entity type
   * prefix (e.g., "customer", "project") and slug is the field slug.
   *
   * @param document the root Tiptap JSON node (type: "doc")
   * @return a map of entity type prefix to set of field slugs
   */
  public Map<String, Set<String>> extractCustomFieldSlugs(Map<String, Object> document) {
    var variableKeys = extractVariableKeys(document);
    var result = new HashMap<String, Set<String>>();

    for (String key : variableKeys) {
      String[] parts = key.split("\\.");
      if (parts.length == 3 && "customFields".equals(parts[1])) {
        result.computeIfAbsent(parts[0], k -> new HashSet<>()).add(parts[2]);
      }
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  private void walkNode(Map<String, Object> node, Set<String> keys, int depth) {
    if (node == null) return;

    String type = (String) node.get("type");
    if (type == null) return;

    Map<String, Object> attrs = (Map<String, Object>) node.getOrDefault("attrs", Map.of());

    switch (type) {
      case "variable" -> {
        String key = (String) attrs.get("key");
        if (key != null && !key.isBlank()) {
          keys.add(key);
        }
      }
      case "conditionalBlock" -> {
        String fieldKey = (String) attrs.get("fieldKey");
        if (fieldKey != null && !fieldKey.isBlank()) {
          keys.add(fieldKey);
        }
      }
      case "clauseBlock" -> {
        if (depth >= MAX_CLAUSE_DEPTH) return;
        String clauseIdStr = (String) attrs.get("clauseId");
        if (clauseIdStr != null) {
          try {
            UUID clauseId = UUID.fromString(clauseIdStr);
            var clauseOpt = clauseRepository.findById(clauseId);
            if (clauseOpt.isPresent()) {
              Clause clause = clauseOpt.get();
              Map<String, Object> bodyJson = clause.getBody();
              if (bodyJson != null) {
                walkNode(bodyJson, keys, depth + 1);
              }
            }
          } catch (IllegalArgumentException ignored) {
            // Invalid UUID, skip
          }
        }
      }
      case "loopTable" -> {
        var columns = (List<Map<String, Object>>) attrs.getOrDefault("columns", List.of());
        String dataSource = (String) attrs.get("dataSource");
        for (var col : columns) {
          String colKey = (String) col.get("key");
          if (colKey != null && !colKey.isBlank() && dataSource != null) {
            keys.add(dataSource + "." + colKey);
          }
        }
      }
    }

    // Recurse into children
    var content = (List<Map<String, Object>>) node.get("content");
    if (content != null) {
      for (var child : content) {
        walkNode(child, keys, depth);
      }
    }
  }
}
