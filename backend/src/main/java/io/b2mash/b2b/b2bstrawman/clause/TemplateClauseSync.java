package io.b2mash.b2b.b2bstrawman.clause;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Syncs {@link TemplateClause} rows from clauseBlock nodes in a template's Tiptap document JSON.
 *
 * <p>The document JSON is the source of truth for clause associations (see ADR-123). This service
 * extracts clauseBlock nodes via recursive DFS, diffs against existing template_clauses rows, and
 * creates/updates/deletes rows to match.
 *
 * <p>This service does NOT declare its own {@code @Transactional} boundary — it piggybacks on the
 * caller's transaction (typically {@code DocumentTemplateService.update()}).
 */
@Service
public class TemplateClauseSync {

  private static final Logger log = LoggerFactory.getLogger(TemplateClauseSync.class);

  private final TemplateClauseRepository templateClauseRepository;
  private final AuditService auditService;

  public TemplateClauseSync(
      TemplateClauseRepository templateClauseRepository, AuditService auditService) {
    this.templateClauseRepository = templateClauseRepository;
    this.auditService = auditService;
  }

  /**
   * Extracted clause reference from a clauseBlock node in the document JSON.
   *
   * @param clauseId the clause UUID
   * @param required whether the clause is required
   */
  record ClauseBlockRef(UUID clauseId, boolean required) {}

  /**
   * Syncs template_clauses rows for the given template based on clauseBlock nodes found in the
   * document content. Uses a smart diff approach: updates in place, creates new rows, deletes
   * removed rows.
   *
   * @param templateId the template ID
   * @param content the Tiptap document JSON (root node with type "doc")
   */
  public void syncClausesFromDocument(UUID templateId, Map<String, Object> content) {
    if (content == null) {
      return;
    }

    // 1. Extract clauseBlock refs from document
    List<ClauseBlockRef> extracted = extractClauseBlocks(content);

    // 2. Load current rows
    List<TemplateClause> existing =
        templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);

    // 3. Index existing by clauseId for O(1) lookup
    Map<UUID, TemplateClause> existingByClauseId =
        existing.stream()
            .collect(Collectors.toMap(TemplateClause::getClauseId, Function.identity()));

    // 4. Diff: determine inserts, updates, deletes
    List<TemplateClause> toInsert = new ArrayList<>();
    List<TemplateClause> toUpdate = new ArrayList<>();
    var extractedClauseIds = new java.util.LinkedHashSet<UUID>();

    for (int i = 0; i < extracted.size(); i++) {
      ClauseBlockRef ref = extracted.get(i);
      extractedClauseIds.add(ref.clauseId());

      TemplateClause existingRow = existingByClauseId.get(ref.clauseId());
      if (existingRow == null) {
        // New clause — INSERT
        toInsert.add(new TemplateClause(templateId, ref.clauseId(), i, ref.required()));
      } else {
        // Existing clause — check for changes
        if (existingRow.getSortOrder() != i || existingRow.isRequired() != ref.required()) {
          existingRow.setSortOrder(i);
          existingRow.setRequired(ref.required());
          toUpdate.add(existingRow);
        }
      }
    }

    // Rows to delete: existing rows whose clauseId is not in extracted set
    List<TemplateClause> toDelete =
        existing.stream().filter(tc -> !extractedClauseIds.contains(tc.getClauseId())).toList();

    // 5. Flush changes
    if (!toDelete.isEmpty()) {
      templateClauseRepository.deleteAll(toDelete);
    }
    if (!toUpdate.isEmpty()) {
      templateClauseRepository.saveAll(toUpdate);
    }
    if (!toInsert.isEmpty()) {
      templateClauseRepository.saveAll(toInsert);
    }

    int totalChanges = toInsert.size() + toUpdate.size() + toDelete.size();
    if (totalChanges > 0) {
      log.info(
          "Synced clauses for template {}: {} added, {} updated, {} removed",
          templateId,
          toInsert.size(),
          toUpdate.size(),
          toDelete.size());

      var details = new LinkedHashMap<String, Object>();
      details.put("operation", "sync");
      details.put("added", toInsert.size());
      details.put("updated", toUpdate.size());
      details.put("removed", toDelete.size());
      details.put("totalClauses", extracted.size());
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("template_clause.synced")
              .entityType("document_template")
              .entityId(templateId)
              .details(details)
              .build());
    }
  }

  /**
   * Extracts clauseBlock references from a Tiptap document JSON tree using recursive DFS.
   *
   * @param document the root document node (must have type "doc")
   * @return list of ClauseBlockRef in document order
   */
  @SuppressWarnings("unchecked")
  static List<ClauseBlockRef> extractClauseBlocks(Map<String, Object> document) {
    List<ClauseBlockRef> result = new ArrayList<>();
    extractClauseBlocksRecursive(document, result);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static void extractClauseBlocksRecursive(
      Map<String, Object> node, List<ClauseBlockRef> result) {
    if (node == null) {
      return;
    }

    String type = (String) node.get("type");
    if ("clauseBlock".equals(type)) {
      Map<String, Object> attrs = (Map<String, Object>) node.get("attrs");
      if (attrs != null) {
        Object clauseIdObj = attrs.get("clauseId");
        if (clauseIdObj != null) {
          try {
            UUID clauseId = UUID.fromString(clauseIdObj.toString());
            Object requiredObj = attrs.get("required");
            boolean required = requiredObj instanceof Boolean b ? b : false;
            result.add(new ClauseBlockRef(clauseId, required));
          } catch (IllegalArgumentException e) {
            log.warn("Skipping clauseBlock with invalid clauseId: {}", clauseIdObj);
          }
        } else {
          log.warn("Skipping clauseBlock with missing clauseId");
        }
      }
    }

    // Recurse into content array
    Object contentObj = node.get("content");
    if (contentObj instanceof List<?> contentList) {
      for (Object child : contentList) {
        if (child instanceof Map<?, ?> childMap) {
          extractClauseBlocksRecursive((Map<String, Object>) childMap, result);
        }
      }
    }
  }
}
