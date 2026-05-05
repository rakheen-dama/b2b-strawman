package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.document.DocumentService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Lists documents attached to a customer or information-request entity. */
@Component
public class ListDocumentsForContextTool implements AssistantTool {

  private final DocumentService documentService;

  public ListDocumentsForContextTool(DocumentService documentService) {
    this.documentService = documentService;
  }

  @Override
  public String name() {
    return "ListDocumentsForContext";
  }

  @Override
  public String description() {
    return "List documents attached to a customer or information-request entity.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "entityType",
            Map.of("type", "string", "enum", List.of("customer", "informationRequest")),
            "entityId",
            Map.of("type", "string", "format", "uuid")),
        "required",
        List.of("entityType", "entityId"));
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("CUSTOMER_VIEW");
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var entityType = (String) input.get("entityType");
    var entityIdStr = (String) input.get("entityId");

    UUID entityId;
    try {
      entityId = UUID.fromString(entityIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid entityId format: " + entityIdStr);
    }

    if ("customer".equals(entityType)) {
      var documents = documentService.listCustomerDocuments(entityId);
      return documents.stream()
          .map(
              d -> {
                var map = new LinkedHashMap<String, Object>();
                map.put("documentId", d.getId().toString());
                map.put("fileName", d.getFileName());
                map.put("contentType", d.getContentType());
                map.put("size", d.getSize());
                map.put("status", d.getStatus().name());
                return map;
              })
          .toList();
    }

    // informationRequest — not yet implemented, return empty
    return List.of();
  }
}
