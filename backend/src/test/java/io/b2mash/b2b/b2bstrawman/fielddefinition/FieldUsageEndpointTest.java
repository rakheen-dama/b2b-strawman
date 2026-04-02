package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldUsageEndpointTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_fu_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Field Usage Test Org", null);

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_fu_owner", "fu_owner@test.com", "FU Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void shouldReturnTemplatesAndClausesReferencingField() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create a field definition
                  var fd =
                      new FieldDefinition(
                          EntityType.CUSTOMER, "Tax Number", "tax_number_usage", FieldType.TEXT);
                  fd = fieldDefinitionRepository.save(fd);

                  // Create a template that references the field via a variable in content
                  var tiptapContent =
                      Map.<String, Object>of(
                          "type",
                          "doc",
                          "content",
                          List.of(
                              Map.<String, Object>of(
                                  "type",
                                  "paragraph",
                                  "content",
                                  List.of(
                                      Map.<String, Object>of(
                                          "type",
                                          "variable",
                                          "attrs",
                                          Map.of(
                                              "key", "customer.customFields.tax_number_usage"))))));

                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.CUSTOMER,
                          "Tax Template",
                          "tax-template-usage",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          tiptapContent);
                  documentTemplateRepository.save(template);

                  // Create a clause that references the field
                  var clauseBody =
                      Map.<String, Object>of(
                          "type",
                          "doc",
                          "content",
                          List.of(
                              Map.<String, Object>of(
                                  "type",
                                  "paragraph",
                                  "content",
                                  List.of(
                                      Map.<String, Object>of(
                                          "type",
                                          "variable",
                                          "attrs",
                                          Map.of(
                                              "key", "customer.customFields.tax_number_usage"))))));

                  var clause =
                      new Clause("Tax Clause", "tax-clause-usage", clauseBody, "compliance");
                  clauseRepository.save(clause);
                }));

    // Now call the usage endpoint
    try {
      // First get the field ID
      var listResult =
          mockMvc
              .perform(
                  get("/api/field-definitions").param("entityType", "CUSTOMER").with(ownerJwt()))
              .andExpect(status().isOk())
              .andReturn();

      String json = listResult.getResponse().getContentAsString();
      List<Map<String, Object>> fields = JsonPath.read(json, "$");
      String fieldId = null;
      for (var f : fields) {
        if ("tax_number_usage".equals(f.get("slug"))) {
          fieldId = f.get("id").toString();
          break;
        }
      }
      assertThat(fieldId).isNotNull();

      // Call usage endpoint
      mockMvc
          .perform(get("/api/field-definitions/" + fieldId + "/usage").with(ownerJwt()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.templates").isArray())
          .andExpect(jsonPath("$.templates.length()").value(1))
          .andExpect(jsonPath("$.templates[0].name").value("Tax Template"))
          .andExpect(jsonPath("$.templates[0].category").value("ENGAGEMENT_LETTER"))
          .andExpect(jsonPath("$.clauses").isArray())
          .andExpect(jsonPath("$.clauses.length()").value(1))
          .andExpect(jsonPath("$.clauses[0].title").value("Tax Clause"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void shouldReturnEmptyUsageForUnreferencedField() throws Exception {
    // Create a field that's not referenced anywhere
    var result =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "PROJECT",
                          "name": "Unused Field Usage",
                          "fieldType": "TEXT",
                          "required": false,
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String fieldId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/api/field-definitions/" + fieldId + "/usage").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.templates").isArray())
        .andExpect(jsonPath("$.templates.length()").value(0))
        .andExpect(jsonPath("$.clauses").isArray())
        .andExpect(jsonPath("$.clauses.length()").value(0));
  }

  @Test
  void shouldDenyMemberAccessToUsageEndpoint() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "TASK",
                          "name": "Access Test Usage",
                          "fieldType": "BOOLEAN",
                          "required": false,
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String fieldId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/api/field-definitions/" + fieldId + "/usage").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fu_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fu_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
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
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
