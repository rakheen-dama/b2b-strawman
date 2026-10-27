package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Structural validation of every shipped Tiptap template body, for the {@code common} and {@code
 * legal-za} packs (legal-za added in Phase 88 626D).
 */
class TemplatePackJsonValidationTest {

  private static final String PACKS_ROOT = "src/main/resources/template-packs/";

  /**
   * Node types the firm's template editor registers ({@code
   * frontend/components/editor/DocumentEditor.tsx:46-58}): StarterKit v3 (doc, paragraph, text,
   * heading, blockquote, bulletList, orderedList, listItem, codeBlock, hardBreak, horizontalRule),
   * the Table extension ({@code :48-51}: table, tableRow, tableCell, tableHeader) and the template
   * scope custom nodes ({@code :40-43}: variable, loopTable, clauseBlock, conditionalBlock — {@code
   * frontend/components/editor/extensions/*.ts:6}). A body using any other node type cannot be
   * loaded in the editor.
   */
  private static final Set<String> EDITOR_NODE_TYPES =
      Set.of(
          "doc",
          "paragraph",
          "text",
          "heading",
          "blockquote",
          "bulletList",
          "orderedList",
          "listItem",
          "codeBlock",
          "hardBreak",
          "horizontalRule",
          "table",
          "tableRow",
          "tableCell",
          "tableHeader",
          "variable",
          "loopTable",
          "clauseBlock",
          "conditionalBlock");

  /**
   * Inline nodes (StarterKit text + hardBreak; {@code variable} is {@code group: "inline"} at
   * {@code frontend/components/editor/extensions/variable.ts:7-8}). {@code conditionalBlock} is
   * {@code group: "block", content: "block+"} ({@code conditionalBlock.ts:7-8}), so its children
   * must be blocks and it must never sit inside a textblock.
   */
  private static final Set<String> INLINE_NODE_TYPES = Set.of("text", "hardBreak", "variable");

  private static final Set<String> TEXTBLOCK_TYPES = Set.of("paragraph", "heading");

  private final ObjectMapper objectMapper = new ObjectMapper();

  private List<Path> jsonTemplateFiles(String pack) throws IOException {
    Path packDir = Path.of(PACKS_ROOT + pack);
    assertThat(packDir).exists();
    try (Stream<Path> files = Files.list(packDir)) {
      List<Path> templates =
          files
              .filter(p -> p.toString().endsWith(".json"))
              .filter(p -> !p.getFileName().toString().equals("pack.json"))
              .sorted()
              .toList();
      assertThat(templates).as("Pack %s should have JSON template files", pack).isNotEmpty();
      return templates;
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"common", "legal-za"})
  void allJsonTemplateFilesParseAsValidJson(String pack) throws IOException {
    for (Path file : jsonTemplateFiles(pack)) {
      String content = Files.readString(file);
      // Should not throw — validates JSON syntax
      JsonNode node = objectMapper.readTree(content);
      assertThat(node).as("File %s should parse as valid JSON", file.getFileName()).isNotNull();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"common", "legal-za"})
  void eachJsonFileHasDocRootWithContentArray(String pack) throws IOException {
    for (Path file : jsonTemplateFiles(pack)) {
      JsonNode doc = objectMapper.readTree(Files.readString(file));

      assertThat(doc.has("type"))
          .as("File %s should have 'type' field", file.getFileName())
          .isTrue();
      assertThat(doc.get("type").asText())
          .as("File %s root type should be 'doc'", file.getFileName())
          .isEqualTo("doc");

      assertThat(doc.has("content"))
          .as("File %s should have 'content' field", file.getFileName())
          .isTrue();
      assertThat(doc.get("content").isArray())
          .as("File %s 'content' should be an array", file.getFileName())
          .isTrue();
      assertThat(doc.get("content").size())
          .as("File %s 'content' array should not be empty", file.getFileName())
          .isGreaterThan(0);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"common", "legal-za"})
  void allVariableNodesHaveNonEmptyKeyAttribute(String pack) throws IOException {
    for (Path file : jsonTemplateFiles(pack)) {
      JsonNode doc = objectMapper.readTree(Files.readString(file));
      assertVariableNodesHaveKeys(doc, file.getFileName().toString());
    }
  }

  /**
   * Phase 88 626D (ruling R-14, finding F6): every body loads in the template editor — only node
   * types the editor registers, no {@code conditionalBlock} inside a paragraph/heading, and every
   * {@code conditionalBlock} has at least one child, all of them block nodes with a {@code
   * fieldKey}.
   */
  @ParameterizedTest
  @ValueSource(strings = {"common", "legal-za"})
  void bodiesUseOnlyEditorNodeTypes_andConditionalBlocksWrapWholeBlocks(String pack)
      throws IOException {
    var violations = new ArrayList<String>();
    for (Path file : jsonTemplateFiles(pack)) {
      JsonNode doc = objectMapper.readTree(Files.readString(file));
      collectShapeViolations(doc, null, file.getFileName().toString(), violations);
    }
    assertThat(violations).as("editor-shape violations in pack %s", pack).isEmpty();
  }

  private void collectShapeViolations(
      JsonNode node, String parentType, String fileName, List<String> violations) {
    String type = node.path("type").asText();
    if (!EDITOR_NODE_TYPES.contains(type)) {
      violations.add(fileName + ": node type '" + type + "' is not registered in the editor");
    }
    if ("conditionalBlock".equals(type)) {
      if (parentType != null && TEXTBLOCK_TYPES.contains(parentType)) {
        violations.add(fileName + ": conditionalBlock inside a " + parentType);
      }
      if (node.path("attrs").path("fieldKey").asText().isBlank()) {
        violations.add(fileName + ": conditionalBlock without a fieldKey");
      }
      JsonNode children = node.path("content");
      if (!children.isArray() || children.isEmpty()) {
        violations.add(fileName + ": conditionalBlock without block content");
      }
      for (JsonNode child : children) {
        if (INLINE_NODE_TYPES.contains(child.path("type").asText())) {
          violations.add(
              fileName
                  + ": conditionalBlock child '"
                  + child.path("type").asText()
                  + "' is inline (must be a block)");
        }
      }
    }
    for (JsonNode child : node.path("content")) {
      collectShapeViolations(child, type, fileName, violations);
    }
  }

  private void assertVariableNodesHaveKeys(JsonNode node, String fileName) {
    if (node.isObject()) {
      if ("variable".equals(node.path("type").asText())) {
        JsonNode attrs = node.get("attrs");
        assertThat(attrs).as("Variable node in %s should have 'attrs'", fileName).isNotNull();
        assertThat(attrs.has("key"))
            .as("Variable node attrs in %s should have 'key'", fileName)
            .isTrue();
        assertThat(attrs.get("key").asText())
            .as("Variable node key in %s should not be empty", fileName)
            .isNotBlank();
      }
      node.fields()
          .forEachRemaining(entry -> assertVariableNodesHaveKeys(entry.getValue(), fileName));
    } else if (node.isArray()) {
      node.forEach(child -> assertVariableNodesHaveKeys(child, fileName));
    }
  }
}
