package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgRoleControllerTest {
  private static final String ORG_ID = "org_role_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private String ownerMemberId;
  private String adminMemberId;
  private String memberMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Role Ctrl Test Org", null);

    ownerMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_rc_owner", "rc_owner@test.com", "RC Owner", "owner");
    adminMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_rc_admin", "rc_admin@test.com", "RC Admin", "admin");
    memberMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_rc_member", "rc_member@test.com", "RC Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void listRoles_returnsSystemAndCustomRoles() throws Exception {
    mockMvc
        .perform(get("/api/org-roles").with(TestJwtFactory.memberJwt(ORG_ID, "user_rc_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
  }

  @Test
  void listRoles_includesMemberCount() throws Exception {
    mockMvc
        .perform(get("/api/org-roles").with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].memberCount").exists());
  }

  @Test
  void getRole_byId_returns200() throws Exception {
    var listResult =
        mockMvc
            .perform(get("/api/org-roles").with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
            .andExpect(status().isOk())
            .andReturn();
    String firstId = JsonPath.read(listResult.getResponse().getContentAsString(), "$[0].id");

    mockMvc
        .perform(
            get("/api/org-roles/" + firstId).with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstId))
        .andExpect(jsonPath("$.name").isString())
        .andExpect(jsonPath("$.slug").isString())
        .andExpect(jsonPath("$.isSystem").isBoolean())
        .andExpect(jsonPath("$.memberCount").isNumber());
  }

  @Test
  void getRole_notFound_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/org-roles/" + UUID.randomUUID())
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRole_valid_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/org-roles")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_rc_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Project Lead",
                      "description": "Manages projects",
                      "capabilities": ["PROJECT_MANAGEMENT", "TEAM_OVERSIGHT"]
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("Project Lead"))
        .andExpect(jsonPath("$.slug").value("project-lead"))
        .andExpect(jsonPath("$.isSystem").value(false))
        .andExpect(jsonPath("$.memberCount").value(0))
        .andExpect(jsonPath("$.capabilities").isArray());
  }

  @Test
  void createRole_duplicateName_returns409() throws Exception {
    mockMvc
        .perform(
            post("/api/org-roles")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_rc_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Unique Role 409", "description": null, "capabilities": []}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/org-roles")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_rc_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "unique role 409", "description": null, "capabilities": []}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void createRole_invalidCapability_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/org-roles")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_rc_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Bad Cap Role", "description": null, "capabilities": ["NOT_A_REAL_CAP"]}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRole_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/org-roles")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_rc_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Forbidden Role", "description": null, "capabilities": []}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateRole_valid_returns200() throws Exception {
    String roleId = createCustomRole("Update Target", Set.of("INVOICING"));

    mockMvc
        .perform(
            put("/api/org-roles/" + roleId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_rc_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Updated Role Name",
                      "description": "Updated description",
                      "capabilities": ["INVOICING", "FINANCIAL_VISIBILITY"]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Role Name"))
        .andExpect(jsonPath("$.slug").value("updated-role-name"))
        .andExpect(jsonPath("$.description").value("Updated description"));
  }

  @Test
  void updateRole_systemRole_returns400() throws Exception {
    var listResult =
        mockMvc
            .perform(get("/api/org-roles").with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
            .andExpect(status().isOk())
            .andReturn();
    List<Map<String, Object>> roles =
        JsonPath.read(listResult.getResponse().getContentAsString(), "$");
    String systemRoleId =
        roles.stream()
            .filter(r -> Boolean.TRUE.equals(r.get("isSystem")))
            .findFirst()
            .map(r -> r.get("id").toString())
            .orElseThrow();

    mockMvc
        .perform(
            put("/api/org-roles/" + systemRoleId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Hacked System Role", "capabilities": []}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteRole_noMembers_returns204() throws Exception {
    String roleId = createCustomRole("Delete Me", Set.of());

    mockMvc
        .perform(
            delete("/api/org-roles/" + roleId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/org-roles/" + roleId).with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRole_withMembers_returns409() throws Exception {
    String roleId = createCustomRole("In Use Role", Set.of("INVOICING"));

    String schema = tenantSchema;
    UUID roleUuid = UUID.fromString(roleId);
    UUID memberUuid = UUID.fromString(memberMemberId);
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              var member = memberRepository.findById(memberUuid).orElseThrow();
              member.setOrgRoleEntity(orgRoleRepository.findById(roleUuid).orElseThrow());
              memberRepository.save(member);
            });

    mockMvc
        .perform(
            delete("/api/org-roles/" + roleId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
        .andExpect(status().isConflict());
  }

  @Test
  void deleteRole_systemRole_returns400() throws Exception {
    var listResult =
        mockMvc
            .perform(get("/api/org-roles").with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
            .andExpect(status().isOk())
            .andReturn();
    List<Map<String, Object>> roles =
        JsonPath.read(listResult.getResponse().getContentAsString(), "$");
    String systemRoleId =
        roles.stream()
            .filter(r -> Boolean.TRUE.equals(r.get("isSystem")))
            .findFirst()
            .map(r -> r.get("id").toString())
            .orElseThrow();

    mockMvc
        .perform(
            delete("/api/org-roles/" + systemRoleId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rc_owner")))
        .andExpect(status().isBadRequest());
  }

  private String createCustomRole(String name, Set<String> capabilities) throws Exception {
    var body =
        """
        {
          "name": "%s",
          "description": "Test role",
          "capabilities": [%s]
        }
        """
            .formatted(
                name,
                capabilities.stream()
                    .map(c -> "\"" + c + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse(""));
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_rc_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }
}
