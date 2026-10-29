package io.b2mash.b2b.b2bstrawman.member;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
class ProjectMemberIntegrationTest {
  private static final String ORG_ID = "org_pm_test";
  private static final String ORG_B_ID = "org_pm_test_b";
  private static final String CANNOT_MANAGE_TITLE = "Cannot manage matter team";
  private static final String CANNOT_MANAGE_DETAIL =
      "Only the matter lead or a firm admin can manage the team";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String ownerMemberId;
  private String adminMemberId;
  private String memberMemberId;
  private String extraMemberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "PM Test Org", null);
    provisioningService.provisionTenant(ORG_B_ID, "PM Test Org B", null);

    ownerMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_pm_owner", "owner@test.com", "Owner", "owner");
    adminMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_pm_admin", "admin@test.com", "Admin", "admin");
    memberMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_pm_member", "member@test.com", "Member", "member");
    extraMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_pm_extra", "extra@test.com", "Extra", "member");

    TestMemberHelper.syncMember(
        mockMvc, ORG_B_ID, "user_pm_tenant_b", "tenantb@test.com", "Tenant B User", "member");
  }

  // --- Creator becomes lead ---

  @Test
  void creatorBecomesLeadOnProjectCreation() throws Exception {
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "Lead Test Project");

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].memberId").value(ownerMemberId))
        .andExpect(jsonPath("$[0].matterRole").value("lead"))
        .andExpect(jsonPath("$[0].orgRole").value("owner"))
        .andExpect(jsonPath("$[0].name").value("Owner"))
        .andExpect(jsonPath("$[0].email").value("owner@test.com"));
  }

  @Test
  void memberListIncludesOrgRoleForAdminAddedAsProjectMember() throws Exception {
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "OrgRole Test Project");

    // Admin created the project (lead), add a regular member
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].orgRole".formatted(adminMemberId)).value("admin"))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].orgRole".formatted(memberMemberId)).value("member"));
  }

  // --- Add member ---

  @Test
  void shouldAddMemberToProject() throws Exception {
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "Add Member Test");

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  void shouldReturn409WhenAddingDuplicateMember() throws Exception {
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "Duplicate Test");

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldReturn404WhenAddingNonexistentMember() throws Exception {
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "Nonexistent Member Test");

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"00000000-0000-0000-0000-000000000000\"}"))
        .andExpect(status().isNotFound());
  }

  // --- Remove member ---

  @Test
  void shouldRemoveMemberFromProject() throws Exception {
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "Remove Member Test");

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            delete("/api/matters/" + projectId + "/members/" + memberMemberId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void shouldReturn400WhenRemovingLead() throws Exception {
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "Remove Lead Test");

    mockMvc
        .perform(
            delete("/api/matters/" + projectId + "/members/" + adminMemberId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn404WhenRemovingNonMember() throws Exception {
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "Remove Non-Member Test");

    mockMvc
        .perform(
            delete("/api/matters/" + projectId + "/members/" + extraMemberId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin")))
        .andExpect(status().isNotFound());
  }

  // --- Lead transfer ---

  @Test
  void shouldTransferLead() throws Exception {
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "Transfer Lead Test");

    // Add another member
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    // Transfer lead from admin to member
    mockMvc
        .perform(
            put("/api/matters/" + projectId + "/members/" + memberMemberId + "/role")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNoContent());

    // Verify roles swapped
    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(adminMemberId))
                .value("member"))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(memberMemberId))
                .value("lead"));
  }

  @Test
  void shouldReturn404WhenTransferTargetNotOnProject() throws Exception {
    var projectId =
        createProject(
            TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "Transfer Not On Project Test");

    mockMvc
        .perform(
            put("/api/matters/" + projectId + "/members/" + memberMemberId + "/role")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNotFound());
  }

  // --- GET /api/members (org members) ---

  @Test
  void shouldListOrgMembers() throws Exception {
    mockMvc
        .perform(get("/api/members").with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].email").exists())
        .andExpect(jsonPath("$[0].orgRole").exists());
  }

  @Test
  void shouldReturn401ForUnauthenticatedMembersRequest() throws Exception {
    mockMvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
  }

  // --- RBAC ---

  @Test
  void memberOnMatterCanListProjectMembers() throws Exception {
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "RBAC List Test");
    addToMatter(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), projectId, memberMemberId);

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  // --- 619.8 canManage gates (ADR-346) ---

  @Test
  void memberNotLeadCannotAddMember_returns403() throws Exception {
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "RBAC Add Test");
    addToMatter(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), projectId, memberMemberId);

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(extraMemberId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value(CANNOT_MANAGE_TITLE))
        .andExpect(jsonPath("$.detail").value(CANNOT_MANAGE_DETAIL));

    // Team unchanged: owner (lead) + member, extra not added
    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[?(@.memberId == '%s')]".formatted(extraMemberId), hasSize(0)));
  }

  @Test
  void memberNotOnMatterCannotAddMember_returns404() throws Exception {
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "RBAC Add Not On Matter");

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(extraMemberId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Matter not found"))
        .andExpect(jsonPath("$.detail").value("No matter found with id " + projectId));
  }

  @Test
  void leadCanAddAndRemoveMember() throws Exception {
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "RBAC Lead Add Test");
    addToMatter(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), projectId, memberMemberId);
    // Owner makes the org Member the matter lead
    mockMvc
        .perform(
            put("/api/matters/" + projectId + "/members/" + memberMemberId + "/role")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNoContent());

    // The lead (org role member) can add: 201 + Location, no body
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(extraMemberId)))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", "/api/matters/" + projectId + "/members/" + extraMemberId))
        .andExpect(content().string(""));

    // ...and remove a non-lead
    mockMvc
        .perform(
            delete("/api/matters/" + projectId + "/members/" + extraMemberId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(memberMemberId))
                .value("lead"));
  }

  @Test
  void memberNotLeadCannotRemoveMember_returns403() throws Exception {
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "RBAC Remove Test");
    addToMatter(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), projectId, memberMemberId);
    addToMatter(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), projectId, extraMemberId);

    mockMvc
        .perform(
            delete("/api/matters/" + projectId + "/members/" + extraMemberId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value(CANNOT_MANAGE_TITLE))
        .andExpect(jsonPath("$.detail").value(CANNOT_MANAGE_DETAIL));

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)));
  }

  @Test
  void memberNotLeadCannotTransferLead_returns403() throws Exception {
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "RBAC Transfer Test");
    addToMatter(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), projectId, memberMemberId);

    mockMvc
        .perform(
            put("/api/matters/" + projectId + "/members/" + memberMemberId + "/role")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pm_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value(CANNOT_MANAGE_TITLE))
        .andExpect(jsonPath("$.detail").value(CANNOT_MANAGE_DETAIL));

    // Lead unchanged
    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner")))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(ownerMemberId)).value("lead"))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(memberMemberId))
                .value("member"));
  }

  @Test
  void adminCanTransferLeadWithoutBeingLead() throws Exception {
    // Owner creates the matter (owner = lead); the admin is NOT on it
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "RBAC Admin Transfer");
    addToMatter(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), projectId, memberMemberId);

    mockMvc
        .perform(
            put("/api/matters/" + projectId + "/members/" + memberMemberId + "/role")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(ownerMemberId))
                .value("member"))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(memberMemberId))
                .value("lead"));
  }

  @Test
  void adminNotOnMatterCanAddMember() throws Exception {
    // Owner creates the matter (owner = lead); the admin is NOT on it
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "RBAC Admin Add");

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(extraMemberId)))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", "/api/matters/" + projectId + "/members/" + extraMemberId))
        .andExpect(content().string(""));

    // The added member is on the team; the admin did not join as a side effect
    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(ownerMemberId)).value("lead"))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(extraMemberId))
                .value("member"));
  }

  @Test
  void ownerOnMatterAsMemberCanTransferLeadToSelf() throws Exception {
    // Admin creates the matter (admin = lead) and adds the owner as a plain member. The owner's own
    // row is loaded by the gate first, so this pins the demote-flush-promote ordering (no 500).
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "RBAC Owner Self Transfer");
    addToMatter(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), projectId, ownerMemberId);

    mockMvc
        .perform(
            put("/api/matters/" + projectId + "/members/" + ownerMemberId + "/role")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"lead\"}"))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(ownerMemberId)).value("lead"))
        .andExpect(
            jsonPath("$[?(@.memberId == '%s')].matterRole".formatted(adminMemberId))
                .value("member"));
  }

  @Test
  void removeLeadStill400EvenForOwnerNotLead() throws Exception {
    var projectId =
        createProject(TestJwtFactory.adminJwt(ORG_ID, "user_pm_admin"), "RBAC Owner Remove Lead");

    mockMvc
        .perform(
            delete("/api/matters/" + projectId + "/members/" + adminMemberId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Cannot remove matter lead"))
        .andExpect(
            jsonPath("$.detail")
                .value("Transfer lead role to another member before removing the current lead"));
  }

  @Test
  void unauthenticatedCannotAccessProjectMembers() throws Exception {
    mockMvc
        .perform(get("/api/matters/00000000-0000-0000-0000-000000000000/members"))
        .andExpect(status().isUnauthorized());
  }

  // --- Tenant isolation ---

  @Test
  void projectMembersAreIsolatedBetweenTenants() throws Exception {
    var projectId =
        createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_pm_owner"), "Tenant Isolation PM");

    // Tenant B cannot see tenant A's project members
    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/members")
                .with(TestJwtFactory.memberJwt(ORG_B_ID, "user_pm_tenant_b")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void orgMembersAreIsolatedBetweenTenants() throws Exception {
    // Tenant B should see only tenant B's members
    mockMvc
        .perform(get("/api/members").with(TestJwtFactory.memberJwt(ORG_B_ID, "user_pm_tenant_b")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].email").value("tenantb@test.com"));
  }

  // --- Helpers ---

  private void addToMatter(JwtRequestPostProcessor jwt, String projectId, String memberId)
      throws Exception {
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/members")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberId)))
        .andExpect(status().isCreated());
  }

  private String createProject(JwtRequestPostProcessor jwt, String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"%s\", \"description\": null}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();

    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }
}
