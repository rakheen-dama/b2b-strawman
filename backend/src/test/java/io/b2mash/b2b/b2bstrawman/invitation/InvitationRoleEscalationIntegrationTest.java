package io.b2mash.b2b.b2bstrawman.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Task C (FR-4 follow-through): an invitation grants its role when the invitee first signs in
 * ({@code MemberFilter.lazyCreateMember}: the pending invitation's stored role → the member's
 * role). {@code POST /api/invitations} needs only {@code TEAM_OVERSIGHT}, which an admin holds.
 * Enforced at the invitation ({@code InvitationService.createInvitation}): the system Owner role is
 * never invited into (by anyone); the system Admin role only by the owner; a role holding an
 * owner-only capability only by the owner (route 3, review round 1). Invitations carry no
 * capability overrides, so there is no {@code +CAP} route.
 *
 * <p>A custom role is applied by the invitation's stored role id, never re-resolved from its slug
 * (custom-role invite fix, plan option A), so the allowed invitations are signed in too and land on
 * the exact role invited into.
 *
 * <p>The Owner/Admin cases invite as an ADMIN, then sign the invitee in (a JWT carrying the invited
 * email) and read what they got ({@code GET /api/me/capabilities}) before checking the invitation
 * answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitationRoleEscalationIntegrationTest {

  private static final String ORG_ID = "org_invitation_escalation_test";
  private static final String OWNER = "user_ies_owner";
  private static final String ADMIN = "user_ies_admin";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invitation Escalation Attorneys", "legal-za");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "ies_owner@test.com", "Inv Owner", "owner");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, ADMIN, "ies_admin@test.com", "Inv Admin", "admin");
  }

  // (1) the system Owner role

  @Test
  void anAdminCannotInviteIntoTheOwnerRole() throws Exception {
    var invited = invite(admin(), "ies_new_owner@test.com", roleId("owner")).andReturn();

    String me = signIn("user_ies_new_owner", "ies_new_owner@test.com");
    assertThat((Boolean) JsonPath.read(me, "$.isOwner"))
        .as("invitee is an owner: %s", me)
        .isFalse();
    assertForbidden(
        invited,
        "Owner role assignment forbidden",
        "Cannot assign the Owner system role to a member");
  }

  @Test
  void evenTheOwnerCannotInviteIntoTheOwnerRole() throws Exception {
    assertForbidden(
        invite(owner(), "ies_new_owner2@test.com", roleId("owner")).andReturn(),
        "Owner role assignment forbidden",
        "Cannot assign the Owner system role to a member");
  }

  // (2) the system Admin role

  @Test
  void anAdminCannotInviteIntoTheAdminRole() throws Exception {
    var invited = invite(admin(), "ies_new_admin@test.com", roleId("admin")).andReturn();

    String me = signIn("user_ies_new_admin", "ies_new_admin@test.com");
    assertThat((Boolean) JsonPath.read(me, "$.isAdmin"))
        .as("invitee is an admin: %s", me)
        .isFalse();
    assertForbidden(
        invited,
        "Admin role assignment restricted",
        "Only owners can assign the Admin system role");
  }

  // (3) a custom role holding an owner-only capability (review round 1): the invitee is granted the
  // invitation's role at first sign-in, so the owner-only rule applies at the invitation itself

  @Test
  void anAdminCannotInviteIntoARoleHoldingAnOwnerOnlyCapability() throws Exception {
    String partner = partnerRole("Partner Invite A");
    assertForbidden(
        invite(admin(), "ies_new_partner@test.com", partner).andReturn(),
        "Owner-only capability",
        "Only the owner can invite into a role holding an owner-only capability:"
            + " SIGN_OFF_TRUST_TAKE_ON.");
  }

  // what stays open

  @Test
  @SuppressWarnings("unchecked")
  void anAdminStillInvitesIntoTheMemberRoleAndOrdinaryRoles() throws Exception {
    invite(admin(), "ies_new_member@test.com", roleId("member")).andExpect(status().isCreated());
    String bookkeeper =
        id(
            mockMvc
                .perform(
                    post("/api/org-roles")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"name\": \"Bookkeeper Invite\", \"description\": \"Test role\","
                                + " \"capabilities\": [\"INVOICING\"]}"))
                .andReturn());
    invite(admin(), "ies_new_bookkeeper@test.com", bookkeeper).andExpect(status().isCreated());

    // the invitees sign in on the roles they were invited into
    String member = signIn("user_ies_new_member", "ies_new_member@test.com");
    assertThat((String) JsonPath.read(member, "$.role")).isEqualTo("Member");
    String me = signIn("user_ies_new_bookkeeper", "ies_new_bookkeeper@test.com");
    assertThat((String) JsonPath.read(me, "$.role"))
        .as("bookkeeper: %s", me)
        .isEqualTo("Bookkeeper Invite");
    assertThat((List<String>) JsonPath.read(me, "$.capabilities")).containsExactly("INVOICING");
    assertThat((Boolean) JsonPath.read(me, "$.isAdmin")).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void theOwnerStillInvitesIntoTheAdminRoleAndAPartnerRole() throws Exception {
    invite(owner(), "ies_owner_admin@test.com", roleId("admin")).andExpect(status().isCreated());
    String me = signIn("user_ies_owner_admin", "ies_owner_admin@test.com");
    assertThat((Boolean) JsonPath.read(me, "$.isAdmin")).isTrue();

    invite(owner(), "ies_owner_partner@test.com", partnerRole("Partner Invite B"))
        .andExpect(status().isCreated());
    // a role holding an owner-only capability, invited by the owner, lands with that role
    String partner = signIn("user_ies_owner_partner", "ies_owner_partner@test.com");
    assertThat((String) JsonPath.read(partner, "$.role"))
        .as("partner: %s", partner)
        .isEqualTo("Partner Invite B");
    assertThat((List<String>) JsonPath.read(partner, "$.capabilities"))
        .containsExactlyInAnyOrder("SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST");
    assertThat((Boolean) JsonPath.read(partner, "$.isOwner")).isFalse();
    assertThat((Boolean) JsonPath.read(partner, "$.isAdmin")).isFalse();
  }

  // --- helpers ---

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private RequestPostProcessor admin() {
    return TestJwtFactory.adminJwt(ORG_ID, ADMIN);
  }

  private org.springframework.test.web.servlet.ResultActions invite(
      RequestPostProcessor jwt, String email, String roleId) throws Exception {
    return mockMvc.perform(
        post("/api/invitations")
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\": \"%s\", \"orgRoleId\": \"%s\"}".formatted(email, roleId)));
  }

  /** The invitee's first request (JIT member creation from the pending invitation). */
  private String signIn(String subject, String email) throws Exception {
    return mockMvc
        .perform(
            get("/api/me/capabilities")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(subject)
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member"))
                                    .claim("email", email))))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String roleId(String slug) throws Exception {
    String roles =
        mockMvc
            .perform(get("/api/org-roles").with(owner()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids =
        JsonPath.read(roles, "$[?(@.slug == '%s' && @.isSystem == true)].id".formatted(slug));
    return ids.getFirst();
  }

  private String partnerRole(String name) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        ("{\"name\": \"%s\", \"description\": \"Test role\", \"capabilities\":"
                                + " [\"SIGN_OFF_TRUST_TAKE_ON\", \"VIEW_TRUST\"]}")
                            .formatted(name)))
            .andReturn());
  }

  private static String id(MvcResult created) throws Exception {
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
  }

  private static void assertForbidden(MvcResult result, String title, String detail)
      throws Exception {
    var response = result.getResponse();
    String body = response.getContentAsString();
    assertThat(response.getStatus()).as("invitation answer %s", body).isEqualTo(403);
    assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(403);
    assertThat((String) JsonPath.read(body, "$.title")).isEqualTo(title);
    assertThat((String) JsonPath.read(body, "$.detail")).isEqualTo(detail);
  }
}
