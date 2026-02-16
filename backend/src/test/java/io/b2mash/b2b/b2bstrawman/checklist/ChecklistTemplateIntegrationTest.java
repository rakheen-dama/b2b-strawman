package io.b2mash.b2b.b2bstrawman.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistTemplateIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cl_crud_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CL CRUD Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_clc_owner", "clc_owner@test.com", "CL CRUD Owner", "owner"));
    // Sync a member user too
    syncMember(ORG_ID, "user_clc_member", "clc_member@test.com", "CL CRUD Member", "member");
  }

  @Test
  void shouldCreateTemplateWithItems() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Test Onboarding",
                          "description": "A test onboarding checklist",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 1,
                          "items": [
                            {
                              "name": "Initial Consultation",
                              "description": "Schedule initial call",
                              "sortOrder": 1,
                              "required": true,
                              "requiresDocument": false
                            },
                            {
                              "name": "Upload Agreement",
                              "description": "Upload signed agreement",
                              "sortOrder": 2,
                              "required": true,
                              "requiresDocument": true,
                              "requiredDocumentLabel": "Signed Agreement"
                            }
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.template.slug").value("test_onboarding"))
            .andExpect(jsonPath("$.template.source").value("ORG_CUSTOM"))
            .andExpect(jsonPath("$.template.active").value(true))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].name").value("Initial Consultation"))
            .andExpect(jsonPath("$.items[1].requiresDocument").value(true))
            .andReturn();

    // Verify GET returns the template with items
    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.template.id");
    mockMvc
        .perform(get("/api/checklist-templates/" + id).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.template.name").value("Test Onboarding"))
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void shouldUpdateTemplateAddingItem() throws Exception {
    // Create template with 2 items
    var createResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Update Add Test",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 0,
                          "items": [
                            {"name": "Item A", "sortOrder": 1, "required": true, "requiresDocument": false},
                            {"name": "Item B", "sortOrder": 2, "required": false, "requiresDocument": false}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String templateId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.template.id");
    String itemAId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.items[0].id");
    String itemBId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.items[1].id");

    // Update: keep both items, add a third
    mockMvc
        .perform(
            put("/api/checklist-templates/" + templateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Update Add Test Updated",
                      "autoInstantiate": false,
                      "sortOrder": 0,
                      "items": [
                        {"id": "%s", "name": "Item A", "sortOrder": 1, "required": true, "requiresDocument": false},
                        {"id": "%s", "name": "Item B", "sortOrder": 2, "required": false, "requiresDocument": false},
                        {"name": "Item C", "sortOrder": 3, "required": true, "requiresDocument": false}
                      ]
                    }
                    """
                        .formatted(itemAId, itemBId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.template.name").value("Update Add Test Updated"));
  }

  @Test
  void shouldUpdateTemplateRemovingItem() throws Exception {
    // Create template with 3 items
    var createResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Update Remove Test",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 0,
                          "items": [
                            {"name": "Keep A", "sortOrder": 1, "required": true, "requiresDocument": false},
                            {"name": "Remove B", "sortOrder": 2, "required": false, "requiresDocument": false},
                            {"name": "Keep C", "sortOrder": 3, "required": true, "requiresDocument": false}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String templateId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.template.id");
    String itemAId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.items[0].id");
    String itemCId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.items[2].id");

    // Update: keep only items A and C (removing B)
    mockMvc
        .perform(
            put("/api/checklist-templates/" + templateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Update Remove Test",
                      "autoInstantiate": false,
                      "sortOrder": 0,
                      "items": [
                        {"id": "%s", "name": "Keep A", "sortOrder": 1, "required": true, "requiresDocument": false},
                        {"id": "%s", "name": "Keep C", "sortOrder": 2, "required": true, "requiresDocument": false}
                      ]
                    }
                    """
                        .formatted(itemAId, itemCId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));

    // Verify via GET
    mockMvc
        .perform(get("/api/checklist-templates/" + templateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void shouldDeactivateTemplate() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Deactivate Test",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 0,
                          "items": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(createResult.getResponse().getContentAsString(), "$.template.id");

    // Deactivate
    mockMvc
        .perform(delete("/api/checklist-templates/" + id).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify deactivated — GET by ID should still return (soft delete)
    mockMvc
        .perform(get("/api/checklist-templates/" + id).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.template.active").value(false));
  }

  @Test
  void shouldCloneTemplate() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Clone Source",
                          "customerType": "INDIVIDUAL",
                          "autoInstantiate": false,
                          "sortOrder": 5,
                          "items": [
                            {"name": "Source Item 1", "sortOrder": 1, "required": true, "requiresDocument": false},
                            {"name": "Source Item 2", "sortOrder": 2, "required": false, "requiresDocument": true, "requiredDocumentLabel": "ID Copy"}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(createResult.getResponse().getContentAsString(), "$.template.id");

    // Clone
    var cloneResult =
        mockMvc
            .perform(
                post("/api/checklist-templates/" + id + "/clone")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"newName": "Cloned Checklist"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.template.name").value("Cloned Checklist"))
            .andExpect(jsonPath("$.template.slug").value("cloned_checklist"))
            .andExpect(jsonPath("$.template.source").value("ORG_CUSTOM"))
            .andExpect(jsonPath("$.template.packId").isEmpty())
            .andExpect(jsonPath("$.template.packTemplateKey").isEmpty())
            .andExpect(jsonPath("$.template.autoInstantiate").value(false))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].name").value("Source Item 1"))
            .andExpect(jsonPath("$.items[1].requiredDocumentLabel").value("ID Copy"))
            .andReturn();

    // Verify cloned template has different ID
    String clonedId =
        JsonPath.read(cloneResult.getResponse().getContentAsString(), "$.template.id");
    assertThat(clonedId).isNotEqualTo(id);
  }

  @Test
  void shouldRejectDuplicateSlug() throws Exception {
    // Create first template
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Duplicate Slug Test",
                      "customerType": "ANY",
                      "autoInstantiate": false,
                      "sortOrder": 0,
                      "items": []
                    }
                    """))
        .andExpect(status().isCreated());

    // Attempt to create with same slug
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Duplicate Slug Test",
                      "customerType": "ANY",
                      "autoInstantiate": false,
                      "sortOrder": 0,
                      "items": []
                    }
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldRejectCrossTemplateDependency() throws Exception {
    // Create template A with 1 item
    var resultA =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Cross Dep Template A",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 0,
                          "items": [
                            {"name": "Item in A", "sortOrder": 1, "required": true, "requiresDocument": false}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String itemAId = JsonPath.read(resultA.getResponse().getContentAsString(), "$.items[0].id");

    // Create template B with item depending on template A's item
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Cross Dep Template B",
                      "customerType": "ANY",
                      "autoInstantiate": false,
                      "sortOrder": 0,
                      "items": [
                        {"name": "Item in B", "sortOrder": 1, "required": true, "requiresDocument": false, "dependsOnItemId": "%s"}
                      ]
                    }
                    """
                        .formatted(itemAId)))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldAllowAdminToCreateTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Admin Created Template",
                      "customerType": "ANY",
                      "autoInstantiate": false,
                      "sortOrder": 0,
                      "items": []
                    }
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void shouldRejectMemberCreateTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Member Attempt",
                      "customerType": "ANY",
                      "autoInstantiate": false,
                      "sortOrder": 0,
                      "items": []
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowMemberToListTemplates() throws Exception {
    mockMvc.perform(get("/api/checklist-templates").with(memberJwt())).andExpect(status().isOk());
  }

  @Test
  void shouldIsolateTemplatesBetweenTenants() throws Exception {
    // Create a template in org1
    var createResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Tenant Isolation Test",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 0,
                          "items": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String templateId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.template.id");

    // Provision a second org
    String org2Id = "org_cl_isolation_test";
    provisioningService.provisionTenant(org2Id, "CL Isolation Org");
    planSyncService.syncPlan(org2Id, "pro-plan");
    syncMember(org2Id, "user_iso_owner", "iso_owner@test.com", "Isolation Owner", "owner");

    JwtRequestPostProcessor org2OwnerJwt =
        jwt()
            .jwt(j -> j.subject("user_iso_owner").claim("o", Map.of("id", org2Id, "rol", "owner")))
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));

    // List templates from org2 — should NOT contain the template created in org1
    var listResult =
        mockMvc
            .perform(get("/api/checklist-templates").with(org2OwnerJwt))
            .andExpect(status().isOk())
            .andReturn();

    String listJson = listResult.getResponse().getContentAsString();
    List<Map<String, Object>> templates = JsonPath.read(listJson, "$");
    List<String> ids =
        templates.stream()
            .map(t -> (String) t.get("id"))
            .collect(java.util.stream.Collectors.toList());
    assertThat(ids).doesNotContain(templateId);

    // GET by ID from org2 — should return 404
    mockMvc
        .perform(get("/api/checklist-templates/" + templateId).with(org2OwnerJwt))
        .andExpect(status().isNotFound());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_clc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_clc_owner").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_clc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  // --- Helpers ---

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
