package io.b2mash.b2b.b2bstrawman.checklist;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChecklistTemplateControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_checklist_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;

  private String createdTemplateId;
  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Checklist Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_cl_ctrl_owner", "cl_ctrl_owner@test.com", "CL Owner", "owner"));
    syncMember(ORG_ID, "user_cl_ctrl_member", "cl_ctrl_member@test.com", "CL Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_cl_315a_custom", "cl_custom@test.com", "CL Custom User", "member"));
    noCapMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_cl_315a_nocap", "cl_nocap@test.com", "CL NoCap User", "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Checklist Manager",
                          "Can manage checklists",
                          Set.of("CUSTOMER_MANAGEMENT")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleId(withCapRole.id());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead CL", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleId(withoutCapRole.id());
              memberRepository.save(noCapMember);
            });
  }

  @Test
  @Order(1)
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
                          "name": "Client Onboarding",
                          "description": "Standard onboarding checklist",
                          "customerType": "INDIVIDUAL",
                          "autoInstantiate": true,
                          "items": [
                            {
                              "name": "Collect ID",
                              "description": "Government-issued ID",
                              "sortOrder": 1,
                              "required": true,
                              "requiresDocument": true,
                              "requiredDocumentLabel": "ID Document"
                            },
                            {
                              "name": "Sign Agreement",
                              "sortOrder": 2,
                              "required": true,
                              "requiresDocument": false
                            }
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Client Onboarding"))
            .andExpect(jsonPath("$.slug").value("client-onboarding"))
            .andExpect(jsonPath("$.customerType").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.source").value("ORG_CUSTOM"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].name").value("Collect ID"))
            .andExpect(jsonPath("$.items[0].requiresDocument").value(true))
            .andReturn();

    createdTemplateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void shouldListActiveTemplates() throws Exception {
    mockMvc
        .perform(get("/api/checklist-templates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @Order(3)
  void shouldGetByIdWithItems() throws Exception {
    mockMvc
        .perform(get("/api/checklist-templates/" + createdTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdTemplateId))
        .andExpect(jsonPath("$.name").value("Client Onboarding"))
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  @Order(4)
  void shouldUpdateTemplateAndReplaceItems() throws Exception {
    mockMvc
        .perform(
            put("/api/checklist-templates/" + createdTemplateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Client Onboarding Updated",
                      "description": "Updated description",
                      "autoInstantiate": false,
                      "items": [
                        {
                          "name": "New Step 1",
                          "sortOrder": 1,
                          "required": true,
                          "requiresDocument": false
                        },
                        {
                          "name": "New Step 2",
                          "sortOrder": 2,
                          "required": false,
                          "requiresDocument": true,
                          "requiredDocumentLabel": "Proof of address"
                        },
                        {
                          "name": "New Step 3",
                          "sortOrder": 3,
                          "required": true,
                          "requiresDocument": false
                        }
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Client Onboarding Updated"))
        .andExpect(jsonPath("$.autoInstantiate").value(false))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].name").value("New Step 1"))
        .andExpect(jsonPath("$.items[1].name").value("New Step 2"))
        .andExpect(jsonPath("$.items[2].name").value("New Step 3"));
  }

  @Test
  @Order(5)
  void shouldSoftDeleteTemplate() throws Exception {
    mockMvc
        .perform(delete("/api/checklist-templates/" + createdTemplateId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it's not in the active list
    mockMvc
        .perform(get("/api/checklist-templates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + createdTemplateId + "')]").doesNotExist());

    // But can still be fetched by ID and shows active=false
    mockMvc
        .perform(get("/api/checklist-templates/" + createdTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  @Order(6)
  void memberCannotCreateTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Member Template",
                      "customerType": "ANY",
                      "autoInstantiate": true,
                      "items": []
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(7)
  void shouldFilterByCustomerType() throws Exception {
    // Create an INDIVIDUAL-only template for filtering test
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Individual Filter Test",
                      "customerType": "INDIVIDUAL",
                      "autoInstantiate": true,
                      "items": []
                    }
                    """))
        .andExpect(status().isCreated());

    // Create a COMPANY-only template
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Company Filter Test",
                      "customerType": "COMPANY",
                      "autoInstantiate": true,
                      "items": []
                    }
                    """))
        .andExpect(status().isCreated());

    // Filter by INDIVIDUAL — should NOT contain COMPANY templates
    mockMvc
        .perform(
            get("/api/checklist-templates").param("customerType", "INDIVIDUAL").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.customerType == 'COMPANY')]").doesNotExist());
  }

  // --- Capability Tests (added in Epic 315A) ---

  @Test
  @Order(100)
  void customRoleWithCapability_accessesChecklistTemplateEndpoint_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(customRoleJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Cap Test Template",
                      "customerType": "ANY",
                      "autoInstantiate": false,
                      "items": []
                    }
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  @Order(101)
  void customRoleWithoutCapability_accessesChecklistTemplateEndpoint_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(noCapabilityJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "NoCap Template",
                      "customerType": "ANY",
                      "autoInstantiate": false,
                      "items": []
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cl_ctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_cl_ctrl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor customRoleJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_cl_315a_custom").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor noCapabilityJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cl_315a_nocap").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
