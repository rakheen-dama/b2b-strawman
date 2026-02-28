package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Startup runner that scans all tenant schemas for {@code legacyHtml} nodes with {@code
 * complexity="simple"} in document templates and clauses, then converts them to proper Tiptap JSON
 * using {@link LegacyContentImporter}.
 *
 * <p>Runs after {@link io.b2mash.b2b.b2bstrawman.provisioning.PackReconciliationRunner} (Order
 * 100). The actual conversion work is performed on a virtual thread so it does not block
 * application startup.
 *
 * <p>This runner is idempotent: once a {@code legacyHtml} node is converted, it is replaced with
 * proper Tiptap nodes and will not match on subsequent runs.
 */
@Component
@Order(200)
public class LegacyContentImportRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LegacyContentImportRunner.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final DocumentTemplateRepository templateRepository;
  private final ClauseRepository clauseRepository;
  private final LegacyContentImporter importer;
  private final TransactionTemplate transactionTemplate;

  public LegacyContentImportRunner(
      OrgSchemaMappingRepository mappingRepository,
      DocumentTemplateRepository templateRepository,
      ClauseRepository clauseRepository,
      LegacyContentImporter importer,
      TransactionTemplate transactionTemplate) {
    this.mappingRepository = mappingRepository;
    this.templateRepository = templateRepository;
    this.clauseRepository = clauseRepository;
    this.importer = importer;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    Thread.startVirtualThread(this::runImport);
  }

  /** Visible for testing -- runs the import synchronously. */
  void runImport() {
    var allMappings = mappingRepository.findAll();
    if (allMappings.isEmpty()) {
      log.info("No tenant schemas found -- skipping legacy content import");
      return;
    }

    log.info("Starting legacy content import for {} tenants", allMappings.size());
    int totalTemplates = 0;
    int totalClauses = 0;

    for (var mapping : allMappings) {
      try {
        var schemaName = mapping.getSchemaName();
        var orgId = mapping.getClerkOrgId();
        int[] counts = {0, 0};

        ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
            .where(RequestScopes.ORG_ID, orgId)
            .run(
                () ->
                    transactionTemplate.executeWithoutResult(
                        tx -> {
                          counts[0] = convertTemplates();
                          counts[1] = convertClauses();
                        }));

        totalTemplates += counts[0];
        totalClauses += counts[1];

        if (counts[0] > 0 || counts[1] > 0) {
          log.info(
              "Legacy content import for tenant {}: {} templates, {} clauses converted",
              schemaName,
              counts[0],
              counts[1]);
        }
      } catch (Exception e) {
        log.error(
            "Failed legacy content import for tenant {} (org {})",
            mapping.getSchemaName(),
            mapping.getClerkOrgId(),
            e);
      }
    }

    log.info(
        "Legacy content import complete: {} templates, {} clauses converted across {} tenants",
        totalTemplates,
        totalClauses,
        allMappings.size());
  }

  @SuppressWarnings("unchecked")
  private int convertTemplates() {
    var templates = templateRepository.findAll();
    int converted = 0;

    for (var template : templates) {
      var content = template.getContent();
      if (content == null) continue;

      var contentList = (List<Map<String, Object>>) content.get("content");
      if (contentList == null) continue;

      var newContent = processContentNodes(contentList);
      if (newContent != null) {
        template.updateContent(
            template.getName(), template.getDescription(), docNode(newContent), template.getCss());
        templateRepository.save(template);
        converted++;
      }
    }

    return converted;
  }

  @SuppressWarnings("unchecked")
  private int convertClauses() {
    var clauses = clauseRepository.findAll();
    int converted = 0;

    for (var clause : clauses) {
      var body = clause.getBody();
      if (body == null) continue;

      var contentList = (List<Map<String, Object>>) body.get("content");
      if (contentList == null) continue;

      var newContent = processContentNodes(contentList);
      if (newContent != null) {
        var newBody = docNode(newContent);
        clause.update(
            clause.getTitle(),
            clause.getSlug(),
            clause.getDescription(),
            newBody,
            clause.getCategory());
        clauseRepository.save(clause);
        converted++;
      }
    }

    return converted;
  }

  /**
   * Process the content array of a doc node. Returns a new content list if any legacyHtml nodes
   * were converted, or null if no changes were needed.
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> processContentNodes(List<Map<String, Object>> contentNodes) {
    boolean changed = false;
    var result = new ArrayList<Map<String, Object>>();

    for (var node : contentNodes) {
      String type = (String) node.get("type");
      if ("legacyHtml".equals(type)) {
        var attrs = (Map<String, Object>) node.getOrDefault("attrs", Map.of());
        String complexity = (String) attrs.get("complexity");

        if ("simple".equals(complexity)) {
          String html = (String) attrs.get("html");
          if (html != null && !html.isBlank()) {
            var converted = importer.convertHtml(html);
            var convertedContent =
                (List<Map<String, Object>>) converted.getOrDefault("content", List.of());
            result.addAll(convertedContent);
            changed = true;
            continue;
          }
        }
      }
      result.add(node);
    }

    return changed ? result : null;
  }

  private Map<String, Object> docNode(List<Map<String, Object>> content) {
    var node = new java.util.LinkedHashMap<String, Object>();
    node.put("type", "doc");
    node.put("content", content);
    return node;
  }
}
