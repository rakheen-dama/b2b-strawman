package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class FieldPackJsonValidationTest {

  private static final String PACK_DIR = "field-packs/";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void commonProjectPackExistsAndParsesCorrectly() throws IOException {
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream(PACK_DIR + "common-project.json")) {
      assertThat(is).as("common-project.json should exist on classpath").isNotNull();

      JsonNode root = objectMapper.readTree(is);
      assertThat(root.get("packId").asText()).isEqualTo("common-project");
      assertThat(root.get("entityType").asText()).isEqualTo("PROJECT");
      // Post-Epic-462: only `category` remains (reference_number and priority promoted to
      // structural columns on Project).
      assertThat(root.get("fields")).hasSize(1);
      assertThat(root.get("fields").get(0).get("slug").asText()).isEqualTo("category");
    }
  }

  @Test
  void commonTaskPackExistsAndParsesCorrectly() throws IOException {
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream(PACK_DIR + "common-task.json")) {
      assertThat(is).as("common-task.json should exist on classpath").isNotNull();

      JsonNode root = objectMapper.readTree(is);
      assertThat(root.get("packId").asText()).isEqualTo("common-task");
      assertThat(root.get("entityType").asText()).isEqualTo("TASK");
      // Post-Epic-462: only `category` remains (priority moved to Project promoted column set,
      // estimated_hours awaiting a Task structural column in a later epic).
      assertThat(root.get("fields")).hasSize(1);
      assertThat(root.get("fields").get(0).get("slug").asText()).isEqualTo("category");
    }
  }

  @Test
  void commonCustomerPackDeleted() {
    // Post-Epic-462: common-customer.json was fully promoted to structural columns and deleted.
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream(PACK_DIR + "common-customer.json")) {
      assertThat(is)
          .as("common-customer.json should NOT exist on classpath after Epic 462 cleanup")
          .isNull();
    } catch (IOException e) {
      // Null stream never throws on close; listed for compiler satisfaction.
    }
  }

  @Test
  void commonInvoicePackDeleted() {
    // Post-Epic-462: common-invoice.json was fully promoted to structural columns and deleted.
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream(PACK_DIR + "common-invoice.json")) {
      assertThat(is)
          .as("common-invoice.json should NOT exist on classpath after Epic 462 cleanup")
          .isNull();
    } catch (IOException e) {
      // Null stream never throws on close; listed for compiler satisfaction.
    }
  }

  @Test
  void allRemainingCommonPackFieldsHaveRequiredProperties() throws IOException {
    String[] packFiles = {"common-project.json", "common-task.json"};

    for (String packFile : packFiles) {
      try (InputStream is = getClass().getClassLoader().getResourceAsStream(PACK_DIR + packFile)) {
        assertThat(is).as("%s should exist on classpath", packFile).isNotNull();

        JsonNode root = objectMapper.readTree(is);
        JsonNode fields = root.get("fields");

        for (JsonNode field : fields) {
          assertThat(field.has("slug")).as("Field in %s should have 'slug'", packFile).isTrue();
          assertThat(field.has("name")).as("Field in %s should have 'name'", packFile).isTrue();
          assertThat(field.has("fieldType"))
              .as("Field in %s should have 'fieldType'", packFile)
              .isTrue();
          assertThat(field.has("sortOrder"))
              .as("Field in %s should have 'sortOrder'", packFile)
              .isTrue();
        }
      }
    }
  }
}
