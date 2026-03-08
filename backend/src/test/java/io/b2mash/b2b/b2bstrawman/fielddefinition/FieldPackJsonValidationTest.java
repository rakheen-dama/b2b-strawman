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
  void commonInvoicePackExistsAndParsesCorrectly() throws IOException {
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream(PACK_DIR + "common-invoice.json")) {
      assertThat(is).as("common-invoice.json should exist on classpath").isNotNull();

      JsonNode root = objectMapper.readTree(is);
      assertThat(root.get("packId").asText()).isEqualTo("common-invoice");
      assertThat(root.get("entityType").asText()).isEqualTo("INVOICE");
      assertThat(root.get("version").asInt()).isEqualTo(1);

      JsonNode group = root.get("group");
      assertThat(group.get("slug").asText()).isEqualTo("invoice_info");
      assertThat(group.get("name").asText()).isEqualTo("Invoice Info");
      assertThat(group.get("autoApply").asBoolean()).isTrue();

      JsonNode fields = root.get("fields");
      assertThat(fields.isArray()).isTrue();
      assertThat(fields).hasSize(5);
    }
  }

  @Test
  void commonProjectPackExistsAndParsesCorrectly() throws IOException {
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream(PACK_DIR + "common-project.json")) {
      assertThat(is).as("common-project.json should exist on classpath").isNotNull();

      JsonNode root = objectMapper.readTree(is);
      assertThat(root.get("packId").asText()).isEqualTo("common-project");
      assertThat(root.get("entityType").asText()).isEqualTo("PROJECT");
      assertThat(root.get("fields")).hasSize(3);
    }
  }

  @Test
  void commonCustomerPackExistsAndParsesCorrectly() throws IOException {
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream(PACK_DIR + "common-customer.json")) {
      assertThat(is).as("common-customer.json should exist on classpath").isNotNull();

      JsonNode root = objectMapper.readTree(is);
      assertThat(root.get("packId").asText()).isEqualTo("common-customer");
      assertThat(root.get("entityType").asText()).isEqualTo("CUSTOMER");
      assertThat(root.get("fields")).hasSize(8);
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
    }
  }

  @Test
  void allFieldPackFieldsHaveRequiredProperties() throws IOException {
    String[] packFiles = {
      "common-invoice.json", "common-project.json", "common-customer.json", "common-task.json"
    };

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

  @Test
  void invoicePackDropdownFieldHasOptions() throws IOException {
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream(PACK_DIR + "common-invoice.json")) {
      JsonNode root = objectMapper.readTree(is);
      JsonNode fields = root.get("fields");

      // Find the tax_type DROPDOWN field
      JsonNode taxTypeField = null;
      for (JsonNode field : fields) {
        if ("tax_type".equals(field.get("slug").asText())) {
          taxTypeField = field;
          break;
        }
      }

      assertThat(taxTypeField).as("tax_type field should exist").isNotNull();
      assertThat(taxTypeField.get("fieldType").asText()).isEqualTo("DROPDOWN");
      assertThat(taxTypeField.get("options").isArray()).isTrue();
      assertThat(taxTypeField.get("options")).hasSize(4);
    }
  }
}
