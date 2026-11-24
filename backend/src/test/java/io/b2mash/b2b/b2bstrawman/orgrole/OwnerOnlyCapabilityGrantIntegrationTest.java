package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * FR-4 (founder ruling 2026-11-24): only an owner may put an owner-only capability ({@link
 * Capability#OWNER_ONLY}) into a role or into a member's effective capabilities. An admin holds
 * {@code TEAM_OVERSIGHT} (every capability but the owner-only ones), which opens role management
 * and role assignment; it must not open a path to the owner-only capabilities themselves — by
 * creating a role holding one, adding one to a role, assigning such a role (to another member or to
 * themselves) or granting one as a {@code +CAP} override. Owners keep all of it (FD-4: a "Partner"
 * role holding {@code SIGN_OFF_TRUST_TAKE_ON} stays grantable by an owner); admins keep every
 * ordinary role operation.
 *
 * <p>Effect probe: {@code GET /api/take-on/sign-off-queue} needs {@code SIGN_OFF_TRUST_TAKE_ON}
 * (annotation) and {@code VIEW_TRUST} (service) — an admin and a plain member are refused it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerOnlyCapabilityGrantIntegrationTest {

  private static final String ORG_ID = "org_owner_only_grant_test";
  private static final String OWNER = "user_oog_owner";
  private static final String ADMIN = "user_oog_admin";
  private static final String TITLE = "Owner-only capability";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Owner Only Grant Attorneys", "legal-za");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "oog_owner@test.com", "Grant Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, ADMIN, "oog_admin@test.com", "Grant Admin", "admin");
  }

  // --- (a) create ---

  @Test
  void anAdminCannotCreateARoleHoldingAnOwnerOnlyCapability() throws Exception {
    createRole(admin(), "Admin Partner", "SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value(TITLE))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Only the owner can create a role holding an owner-only capability:"
                        + " SIGN_OFF_TRUST_TAKE_ON."));
    createRole(admin(), "Admin Reconciler", "FINANCIAL_RECONCILE", "OVERRIDE_MATTER_CLOSURE")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value(TITLE))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Only the owner can create a role holding an owner-only capability:"
                        + " FINANCIAL_RECONCILE, OVERRIDE_MATTER_CLOSURE."));
    assertThat(roleNames()).doesNotContain("Admin Partner", "Admin Reconciler");
  }

  // --- (b) update ---

  @Test
  void anAdminCannotAddAnOwnerOnlyCapabilityToARole() throws Exception {
    String roleId = id(createRole(admin(), "Trust Viewer B", "VIEW_TRUST"));

    updateCapabilities(admin(), roleId, "VIEW_TRUST", "SIGN_OFF_TRUST_TAKE_ON")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value(TITLE))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Only the owner can add an owner-only capability to a role:"
                        + " SIGN_OFF_TRUST_TAKE_ON."));
    assertThat(roleCapabilities(roleId)).containsExactly("VIEW_TRUST");
  }

  // --- (c) assign to another member ---

  @Test
  void anAdminCannotAssignARoleHoldingAnOwnerOnlyCapabilityToAnotherMember() throws Exception {
    String memberId = syncMember("user_oog_target_c", "member");
    String partnerRole =
        id(
            createRole(
                owner(),
                "Partner C",
                "SIGN_OFF_TRUST_TAKE_ON",
                "VIEW_TRUST",
                "FINANCIAL_RECONCILE"));

    var assigned = assign(admin(), memberId, partnerRole).andReturn();

    // the effect: the member must not reach the owner-only sign-off endpoint
    signOffQueue("user_oog_target_c").andExpect(status().isForbidden());
    assertThat(effectiveCapabilities(memberId))
        .doesNotContain("SIGN_OFF_TRUST_TAKE_ON", "FINANCIAL_RECONCILE");
    assertForbidden(
        assigned,
        "Only the owner can assign a role holding an owner-only capability:"
            + " FINANCIAL_RECONCILE, SIGN_OFF_TRUST_TAKE_ON.");
  }

  // --- (d) assign to themselves ---

  @Test
  void anAdminCannotAssignARoleHoldingAnOwnerOnlyCapabilityToThemselves() throws Exception {
    String selfAdminId = syncMember("user_oog_self_admin", "admin");
    String partnerRole =
        id(createRole(owner(), "Partner D", "SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST"));
    var self = TestJwtFactory.adminJwt(ORG_ID, "user_oog_self_admin");

    var assigned = assign(self, selfAdminId, partnerRole).andReturn();

    signOffQueue("user_oog_self_admin").andExpect(status().isForbidden());
    // the admin's own view (/api/me): still on the system Admin role, without the capability
    var me = myCapabilities("user_oog_self_admin");
    assertThat((String) JsonPath.read(me, "$.role")).isEqualTo("Admin");
    assertThat((List<String>) JsonPath.read(me, "$.capabilities"))
        .doesNotContain("SIGN_OFF_TRUST_TAKE_ON");
    assertForbidden(
        assigned,
        "Only the owner can assign a role holding an owner-only capability:"
            + " SIGN_OFF_TRUST_TAKE_ON.");
  }

  // --- (e) +CAP override ---

  @Test
  void anAdminCannotGrantAnOwnerOnlyCapabilityAsAnOverride() throws Exception {
    String memberId = syncMember("user_oog_target_e", "member");
    String viewerRole = id(createRole(admin(), "Trust Viewer E", "VIEW_TRUST"));

    var assigned =
        assign(admin(), memberId, viewerRole, "+SIGN_OFF_TRUST_TAKE_ON", "+FINANCIAL_RECONCILE")
            .andReturn();

    signOffQueue("user_oog_target_e").andExpect(status().isForbidden());
    assertThat(effectiveCapabilities(memberId))
        .doesNotContain("SIGN_OFF_TRUST_TAKE_ON", "FINANCIAL_RECONCILE");
    assertForbidden(
        assigned,
        "Only the owner can grant an owner-only capability:"
            + " FINANCIAL_RECONCILE, SIGN_OFF_TRUST_TAKE_ON.");
  }

  // --- what admins keep ---

  @Test
  void anAdminStillManagesAndAssignsOrdinaryRoles() throws Exception {
    String memberId = syncMember("user_oog_target_ord", "member");
    String roleId = id(createRole(admin(), "Bookkeeper", "INVOICING"));
    updateCapabilities(admin(), roleId, "INVOICING", "VIEW_TRUST")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.length()").value(2));
    assign(admin(), memberId, roleId, "+PROJECT_MANAGEMENT", "-INVOICING")
        .andExpect(status().isOk());
    assertThat(effectiveCapabilities(memberId))
        .contains("VIEW_TRUST", "PROJECT_MANAGEMENT")
        .doesNotContain("INVOICING");

    // an owner's Partner role: an admin may still rename it or take an owner-only capability away
    String partnerRole =
        id(createRole(owner(), "Partner Ord", "SIGN_OFF_TRUST_TAKE_ON", "FINANCIAL_RECONCILE"));
    mockMvc
        .perform(
            put("/api/org-roles/" + partnerRole)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Partner Ord Renamed\"}"))
        .andExpect(status().isOk());
    updateCapabilities(admin(), partnerRole, "SIGN_OFF_TRUST_TAKE_ON").andExpect(status().isOk());
    assertThat(roleCapabilities(partnerRole)).containsExactly("SIGN_OFF_TRUST_TAKE_ON");
  }

  // --- review round 1: the rule is about the member's EFFECTIVE owner-only capabilities ---
  // (a non-owner is refused only when the change adds one the member does not hold already)

  @Test
  void anAdminCanEditOrdinaryOverridesOfAMemberAlreadyOnAPartnerRole() throws Exception {
    String memberId = syncMember("user_oog_partner_r1a", "member");
    String partnerRole =
        id(createRole(owner(), "Partner R1A", "SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST"));
    assign(owner(), memberId, partnerRole).andExpect(status().isOk());

    assign(admin(), memberId, partnerRole, "+INVOICING").andExpect(status().isOk());
    assertThat(effectiveCapabilities(memberId))
        .contains("SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST", "INVOICING");
  }

  @Test
  void anAdminCanTakeAnOwnerOnlyCapabilityAwayFromAPartnerMember() throws Exception {
    String memberId = syncMember("user_oog_partner_r1b", "member");
    String partnerRole =
        id(createRole(owner(), "Partner R1B", "SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST"));
    assign(owner(), memberId, partnerRole).andExpect(status().isOk());

    assign(admin(), memberId, partnerRole, "-SIGN_OFF_TRUST_TAKE_ON").andExpect(status().isOk());
    assertThat(effectiveCapabilities(memberId))
        .contains("VIEW_TRUST")
        .doesNotContain("SIGN_OFF_TRUST_TAKE_ON");
  }

  @Test
  void anAdminCannotDropAMinusOverrideThatWouldRestoreAnOwnerOnlyCapability() throws Exception {
    String memberId = syncMember("user_oog_partner_r1c", "member");
    String partnerRole =
        id(createRole(owner(), "Partner R1C", "SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST"));
    assign(owner(), memberId, partnerRole, "-SIGN_OFF_TRUST_TAKE_ON").andExpect(status().isOk());

    var restored = assign(admin(), memberId, partnerRole).andReturn();

    assertThat(effectiveCapabilities(memberId)).doesNotContain("SIGN_OFF_TRUST_TAKE_ON");
    assertForbidden(
        restored, "Only the owner can grant an owner-only capability: SIGN_OFF_TRUST_TAKE_ON.");
  }

  // --- what owners keep (FD-4) ---

  @Test
  void anOwnerCanStillCreateUpdateAndAssignOwnerOnlyCapabilities() throws Exception {
    String partnerId = syncMember("user_oog_partner", "member");
    String partnerRole = id(createRole(owner(), "Partner", "SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST"));
    updateCapabilities(
            owner(), partnerRole, "SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST", "FINANCIAL_RECONCILE")
        .andExpect(status().isOk());
    assign(owner(), partnerId, partnerRole, "+OVERRIDE_MATTER_CLOSURE").andExpect(status().isOk());

    signOffQueue("user_oog_partner").andExpect(status().isOk());
    assertThat(effectiveCapabilities(partnerId))
        .contains(
            "SIGN_OFF_TRUST_TAKE_ON",
            "VIEW_TRUST",
            "FINANCIAL_RECONCILE",
            "OVERRIDE_MATTER_CLOSURE");
  }

  // --- helpers ---

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private RequestPostProcessor admin() {
    return TestJwtFactory.adminJwt(ORG_ID, ADMIN);
  }

  private String syncMember(String subject, String role) throws Exception {
    return TestMemberHelper.syncMember(
        mockMvc, ORG_ID, subject, subject + "@test.com", subject, role);
  }

  private ResultActions createRole(RequestPostProcessor jwt, String name, String... capabilities)
      throws Exception {
    return mockMvc.perform(
        post("/api/org-roles")
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"name\": \"%s\", \"description\": \"Test role\", \"capabilities\": %s}"
                    .formatted(name, toJson(capabilities))));
  }

  private ResultActions updateCapabilities(
      RequestPostProcessor jwt, String roleId, String... capabilities) throws Exception {
    return mockMvc.perform(
        put("/api/org-roles/" + roleId)
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"capabilities\": %s}".formatted(toJson(capabilities))));
  }

  private ResultActions assign(
      RequestPostProcessor jwt, String memberId, String roleId, String... overrides)
      throws Exception {
    return mockMvc.perform(
        put("/api/members/" + memberId + "/role")
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"orgRoleId\": \"%s\", \"capabilityOverrides\": %s}"
                    .formatted(roleId, toJson(overrides))));
  }

  private ResultActions signOffQueue(String subject) throws Exception {
    return mockMvc.perform(
        get("/api/take-on/sign-off-queue").with(TestJwtFactory.customRoleJwt(ORG_ID, subject)));
  }

  private List<String> effectiveCapabilities(String memberId) throws Exception {
    var result =
        mockMvc
            .perform(get("/api/members/" + memberId + "/capabilities").with(owner()))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.effectiveCapabilities");
  }

  private String myCapabilities(String subject) throws Exception {
    return mockMvc
        .perform(get("/api/me/capabilities").with(TestJwtFactory.customRoleJwt(ORG_ID, subject)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private List<String> roleCapabilities(String roleId) throws Exception {
    var result =
        mockMvc
            .perform(get("/api/org-roles/" + roleId).with(owner()))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.capabilities");
  }

  private List<String> roleNames() throws Exception {
    var result =
        mockMvc.perform(get("/api/org-roles").with(owner())).andExpect(status().isOk()).andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$[*].name");
  }

  private static String id(ResultActions created) throws Exception {
    return JsonPath.read(
        created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
        "$.id");
  }

  private static void assertForbidden(MvcResult result, String detail) throws Exception {
    var response = result.getResponse();
    String body = response.getContentAsString();
    assertThat(response.getStatus()).as("status of %s", body).isEqualTo(403);
    assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(403);
    assertThat((String) JsonPath.read(body, "$.title")).isEqualTo(TITLE);
    assertThat((String) JsonPath.read(body, "$.detail")).isEqualTo(detail);
  }

  private static String toJson(String... values) {
    return JsonMapper.builder().build().writeValueAsString(List.of(values));
  }
}
