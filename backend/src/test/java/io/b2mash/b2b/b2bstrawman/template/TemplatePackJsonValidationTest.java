package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplatePackJsonValidationTest {

  private static final String PACK_DIR = "src/main/resources/template-packs/common";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private List<Path> jsonTemplateFiles;

  @BeforeAll
  void setup() throws IOException {
    Path packDir = Path.of(PACK_DIR);
    assertThat(packDir).exists();

    try (Stream<Path> files = Files.list(packDir)) {
      jsonTemplateFiles =
          files
              .filter(p -> p.toString().endsWith(".json"))
              .filter(p -> !p.getFileName().toString().equals("pack.json"))
              .toList();
    }
    assertThat(jsonTemplateFiles).as("Should have at least one JSON template file").isNotEmpty();
  }

  @Test
  void allJsonTemplateFilesParseAsValidJson() throws IOException {
    for (Path file : jsonTemplateFiles) {
      String content = Files.readString(file);
      // Should not throw — validates JSON syntax
      JsonNode node = objectMapper.readTree(content);
      assertThat(node).as("File %s should parse as valid JSON", file.getFileName()).isNotNull();
    }
  }

  @Test
  void eachJsonFileHasDocRootWithContentArray() throws IOException {
    for (Path file : jsonTemplateFiles) {
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

  @Test
  void allVariableNodesHaveNonEmptyKeyAttribute() throws IOException {
    for (Path file : jsonTemplateFiles) {
      JsonNode doc = objectMapper.readTree(Files.readString(file));
      assertVariableNodesHaveKeys(doc, file.getFileName().toString());
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
