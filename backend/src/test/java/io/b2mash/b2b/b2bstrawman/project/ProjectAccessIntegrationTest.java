package io.b2mash.b2b.b2bstrawman.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectAccessIntegrationTest {
  private static final String ORG_ID = "org_access_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String ownerMemberId;
  private String adminMemberId;
  private String leadMemberId;
  private String regularMemberId;
  private String nonMemberMemberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Access Test Org", null);

    ownerMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_access_owner", "access_owner@test.com", "Owner", "owner");
    adminMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_access_admin", "access_admin@test.com", "Admin", "admin");
    leadMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_access_lead", "access_lead@test.com", "Lead", "member");
    regularMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_access_regular", "access_regular@test.com", "Regular", "member");
    nonMemberMemberId =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_access_nonmember",
            "access_nonmember@test.com",
            "NonMember",
            "member");

    // Assign system roles to owner and admin members for capability-based auth
    var tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(ownerMemberId))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember =
                  memberRepository.findById(UUID.fromString(ownerMemberId)).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);

              var adminRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "admin".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var adminMember =
                  memberRepository.findById(UUID.fromString(adminMemberId)).orElseThrow();
              adminMember.setOrgRoleEntity(adminRole);
              memberRepository.save(adminMember);
            });
  }

  // --- Filtered listing ---

  @Test
  void ownerSeesAllProjects() throws Exception {
    // Lead creates a project (only lead is on it)
    createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Owner Sees All");

    mockMvc
        .perform(get("/api/projects").with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Owner Sees All')]").exists());
  }

  @Test
  void adminSeesAllProjects() throws Exception {
    createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Admin Sees All");

    mockMvc
        .perform(get("/api/projects").with(TestJwtFactory.adminJwt(ORG_ID, "user_access_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Admin Sees All')]").exists());
  }

  @Test
  void memberSeesOnlyTheirProjects() throws Exception {
    // Lead creates a project — nonMember is NOT added
    createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Not For NonMember");

    mockMvc
        .perform(
            get("/api/projects").with(TestJwtFactory.memberJwt(ORG_ID, "user_access_nonmember")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Not For NonMember')]").doesNotExist());
  }

  @Test
  void memberSeesProjectsTheyAreOn() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Member Is On This");

    // Add regular member to this project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(regularMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/projects").with(TestJwtFactory.memberJwt(ORG_ID, "user_access_regular")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Member Is On This')]").exists());
  }

  @Test
  void listingIncludesProjectRole() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Role In Listing");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(regularMemberId)))
        .andExpect(status().isCreated());

    // Creator (owner) sees their lead role
    mockMvc
        .perform(get("/api/projects").with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Role In Listing')].projectRole").value("lead"));

    // Regular member sees their role
    mockMvc
        .perform(get("/api/projects").with(TestJwtFactory.memberJwt(ORG_ID, "user_access_regular")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Role In Listing')].projectRole").value("member"));
  }

  // --- Single project access ---

  @Test
  void nonMemberGets404OnGetProject() throws Exception {
    var projectId =
        createProjectAs(
            TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "No Access For NonMember");

    mockMvc
        .perform(
            get("/api/projects/" + projectId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_access_nonmember")))
        .andExpect(status().isNotFound());
  }

  @Test
  void ownerCanGetAnyProject() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Owner Can Get");

    mockMvc
        .perform(
            get("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Owner Can Get"));
  }

  @Test
  void adminCanGetAnyProject() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Admin Can Get");

    mockMvc
        .perform(
            get("/api/projects/" + projectId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_access_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Admin Can Get"));
  }

  @Test
  void projectMemberCanGetProject() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Regular Can Get");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(regularMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/projects/" + projectId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_access_regular")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Regular Can Get"))
        .andExpect(jsonPath("$.projectRole").value("member"));
  }

  @Test
  void getProjectIncludesProjectRole() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Get With Role");

    // Owner is not on the project member list (unless they created it — they did as owner)
    mockMvc
        .perform(
            get("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectRole").value("lead"));
  }

  @Test
  void adminNotOnProjectSeesNullRole() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Admin Not Member");

    mockMvc
        .perform(
            get("/api/projects/" + projectId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_access_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Admin Not Member"))
        .andExpect(jsonPath("$.projectRole").isEmpty());
  }

  @Test
  void ownerNotOnProjectSeesNullRole() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.adminJwt(ORG_ID, "user_access_admin"), "Owner Not Member");

    mockMvc
        .perform(
            get("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Owner Not Member"))
        .andExpect(jsonPath("$.projectRole").isEmpty());
  }

  // --- Create project (requires PROJECT_MANAGEMENT capability) ---

  @Test
  void memberWithoutCapabilityCannotCreateProject() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_access_regular"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Member Created", "description": null}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void creatorBecomesLeadAndCanGetTheirProject() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Creator Is Lead");

    mockMvc
        .perform(
            get("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectRole").value("lead"));
  }

  // --- Update project ---

  @Test
  void leadCanUpdateProject() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Lead Can Update");

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Lead Updated", "description": "updated by lead"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lead Updated"));
  }

  @Test
  void adminCanUpdateAnyProject() throws Exception {
    var projectId =
        createProjectAs(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Admin Can Update");

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_access_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Admin Updated", "description": "updated by admin"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Admin Updated"));
  }

  @Test
  void regularMemberCannotUpdateProject() throws Exception {
    var projectId =
        createProjectAs(
            TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "Regular Cannot Update");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(regularMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_access_regular"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Should Fail", "description": null}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void nonMemberGets404OnUpdateProject() throws Exception {
    var projectId =
        createProjectAs(
            TestJwtFactory.ownerJwt(ORG_ID, "user_access_owner"), "NonMember Cannot Update");

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_access_nonmember"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Should Not See", "description": null}
                    """))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private String createProjectAs(JwtRequestPostProcessor jwt, String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"%s\", \"description\": null}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();

    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }
}
