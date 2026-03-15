package io.b2mash.b2b.b2bstrawman.template;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplateFieldPackLinkageTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_fpl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;

  private String templateWithFieldsId;
  private String templateNoFieldsId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "FPL Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_fpl_owner", "fpl_owner@test.com", "FPL Owner", "owner");

    // Create field definitions with packId in tenant context
    String tenantId =
        io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator.generateSchemaName(ORG_ID);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .run(
            () -> {
              var fd1 =
                  new FieldDefinition(
                      EntityType.CUSTOMER, "FPL Test Alpha", "fpl_test_alpha", FieldType.TEXT);
              fd1.setPackId("fpl-test-pack");
              fd1.setPackFieldKey("fpl_test_alpha");
              fieldDefinitionRepository.save(fd1);

              var fd2 =
                  new FieldDefinition(
                      EntityType.CUSTOMER, "FPL Test Beta", "fpl_test_beta", FieldType.TEXT);
              fd2.setPackId("fpl-test-pack");
              fd2.setPackFieldKey("fpl_test_beta");
              fieldDefinitionRepository.save(fd2);
            });

    // Create template referencing custom fields (2 found + 1 missing)
    var contentWithFields =
        TestDocumentBuilder.doc()
            .heading(1, "Invoice")
            .variable("customer.customFields.fpl_test_alpha")
            .variable("customer.customFields.fpl_test_beta")
            .variable("customer.customFields.fpl_test_missing")
            .variable("customer.name")
            .build();

    templateWithFieldsId = createTemplate("FPL Template With Fields", contentWithFields);

    // Create template with no custom field variables
    var contentNoFields =
        TestDocumentBuilder.doc()
            .heading(1, "Simple Letter")
            .variable("customer.name")
            .variable("project.name")
            .build();

    templateNoFieldsId = createTemplate("FPL Template No Fields", contentNoFields);
  }

  @Test
  void shouldReturnFieldPackStatusForTemplateWithCustomFields() throws Exception {
    // Template references 3 custom field slugs:
    // - fpl_test_alpha and fpl_test_beta exist with packId="fpl-test-pack" → applied
    // - fpl_test_missing has no FieldDefinition → synthetic "common-customer" pack
    //   (common-customer IS applied because the field pack seeder created fields)
    mockMvc
        .perform(
            get("/api/templates/" + templateWithFieldsId + "/required-field-packs")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        // fpl-test-pack is present and applied (order may vary)
        .andExpect(jsonPath("$[?(@.packId == 'fpl-test-pack')].applied").value(true))
        .andExpect(jsonPath("$[?(@.packId == 'fpl-test-pack')].missingFields.length()").value(0))
        // common-customer has the missing field
        .andExpect(jsonPath("$[?(@.packId == 'common-customer')].applied").value(true))
        .andExpect(
            jsonPath("$[?(@.packId == 'common-customer')].missingFields[0]")
                .value("customer.customFields.fpl_test_missing"));
  }

  @Test
  void shouldReturnEmptyListForTemplateWithNoCustomFields() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/" + templateNoFieldsId + "/required-field-packs").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void shouldRejectMemberAccess() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/" + templateWithFieldsId + "/required-field-packs")
                .with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fpl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fpl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  // --- Helpers ---

  private String createTemplate(String name, Map<String, Object> content) throws Exception {
    var requestBody = new java.util.LinkedHashMap<String, Object>();
    requestBody.put("name", name);
    requestBody.put("category", "ENGAGEMENT_LETTER");
    requestBody.put("primaryEntityType", "CUSTOMER");
    requestBody.put("content", content);

    var result =
        mockMvc
            .perform(
                post("/api/templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "%s",
                      "email": "%s",
                      "name": "%s",
                      "avatarUrl": null,
                      "orgRole": "%s"
                    }
                    """
                        .formatted(orgId, clerkUserId, email, name, orgRole)))
        .andExpect(status().isCreated());
  }
}
