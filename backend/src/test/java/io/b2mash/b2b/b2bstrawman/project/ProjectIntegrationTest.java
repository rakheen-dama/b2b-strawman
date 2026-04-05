package io.b2mash.b2b.b2bstrawman.project;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
class ProjectIntegrationTest {
  private static final String ORG_ID = "org_project_test";
  private static final String ORG_B_ID = "org_project_test_b";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionTenants() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Test Org", null);
    provisioningService.provisionTenant(ORG_B_ID, "Project Test Org B", null);

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "proj_owner@test.com", "Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_admin", "proj_admin@test.com", "Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_member", "proj_member@test.com", "Member", "member");
    TestMemberHelper.syncMember(
        mockMvc, ORG_B_ID, "user_tenant_b", "proj_tenantb@test.com", "Tenant B User", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Assign system owner role to owner member for capability-based auth
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember = memberRepository.findById(ownerMemberId).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);
            });

    // Sync custom-role members for capability tests
    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_proj_314b_custom",
                "proj_custom@test.com",
                "Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_proj_314b_nocap",
                "proj_nocap@test.com",
                "NoCap User",
                "member"));

    // Assign OrgRoles within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Project Manager", "Has project cap", Set.of("PROJECT_MANAGEMENT")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead", "No project cap", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  // --- CRUD happy path ---

  @Test
  void shouldCreateAndGetProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Test Project", "description": "A test project"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Project"))
            .andExpect(jsonPath("$.description").value("A test project"))
            .andExpect(jsonPath("$.createdBy").value(matchesPattern(UUID_PATTERN)))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists())
            .andExpect(jsonPath("$.projectRole").value("lead"))
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    // Owner can always get any project
    mockMvc
        .perform(get("/api/projects/" + id).with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Project"))
        .andExpect(jsonPath("$.description").value("A test project"));
  }

  @Test
  void shouldListProjects() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "List Test 1", "description": null}
                    """))
        .andExpect(status().isCreated());

    // Admin sees all projects
    mockMvc
        .perform(get("/api/projects").with(TestJwtFactory.adminJwt(ORG_ID, "user_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
  }

  @Test
  void shouldUpdateProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Before Update", "description": "Original desc"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(
            put("/api/projects/" + id)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "After Update", "description": "Updated desc"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("After Update"))
        .andExpect(jsonPath("$.description").value("Updated desc"));
  }

  @Test
  void shouldDeleteProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "To Delete", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(delete("/api/projects/" + id).with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/projects/" + id).with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner")))
        .andExpect(status().isNotFound());
  }

  // --- Validation errors ---

  @Test
  void shouldReject400WhenNameIsBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "", "description": "desc"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenNameIsMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description": "desc"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenNameTooLong() throws Exception {
    String longName = "a".repeat(256);
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "%s", "description": "desc"}
                    """
                        .formatted(longName)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenDescriptionTooLong() throws Exception {
    String longDesc = "a".repeat(2001);
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Valid\", \"description\": \"" + longDesc + "\"}"))
        .andExpect(status().isBadRequest());
  }

  // --- Not found ---

  @Test
  void shouldReturn404ForNonexistentProject() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/00000000-0000-0000-0000-000000000000")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404WhenUpdatingNonexistentProject() throws Exception {
    mockMvc
        .perform(
            put("/api/projects/00000000-0000-0000-0000-000000000000")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Ghost", "description": "nope"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404WhenDeletingNonexistentProject() throws Exception {
    mockMvc
        .perform(
            delete("/api/projects/00000000-0000-0000-0000-000000000000")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner")))
        .andExpect(status().isNotFound());
  }

  // --- RBAC ---

  @Test
  void memberWithoutCapabilityCannotCreateProject() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Member Created", "description": null}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCannotDeleteProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "RBAC Delete Test", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(
            delete("/api/projects/" + id).with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateButCannotDelete() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Admin Test", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(delete("/api/projects/" + id).with(TestJwtFactory.adminJwt(ORG_ID, "user_admin")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanUpdateProject() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Admin Update", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(
            put("/api/projects/" + id)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Admin Updated", "description": "updated"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void unauthenticatedUserCannotAccessProjects() throws Exception {
    mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
  }

  // --- Tenant isolation ---

  @Test
  void projectsAreIsolatedBetweenTenants() throws Exception {
    // Create project in tenant A
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Tenant A Isolation", "description": "Belongs to A"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = TestEntityHelper.extractIdFromLocation(createResult);

    // Verify visible from tenant A (owner can see all)
    mockMvc
        .perform(
            get("/api/projects/" + projectId).with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Tenant A Isolation"));

    // Verify NOT visible from tenant B
    mockMvc
        .perform(
            get("/api/projects/" + projectId)
                .with(TestJwtFactory.memberJwt(ORG_B_ID, "user_tenant_b")))
        .andExpect(status().isNotFound());

    // Verify tenant B list doesn't include tenant A's project
    mockMvc
        .perform(get("/api/projects").with(TestJwtFactory.memberJwt(ORG_B_ID, "user_tenant_b")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", everyItem(not("Tenant A Isolation"))));
  }

  // --- Capability Tests ---

  @Test
  void customRoleWithCapability_accessesProjectEndpoint_returns200() throws Exception {
    // Create a project first so unbilled-summary has something to query
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Cap Test Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(
            get("/api/projects/" + id + "/unbilled-summary")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_proj_314b_custom")))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesProjectEndpoint_returns403() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "NoCap Test Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(
            get("/api/projects/" + id + "/unbilled-summary")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_proj_314b_nocap")))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

}
