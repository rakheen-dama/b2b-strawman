package io.b2mash.b2b.b2bstrawman.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Founder ruling "revoke always wins" (PR #1859, replaces FR-9), on the real HTTP path in Keycloak
 * mode (the Keycloak admin client mocked): revoking a pending invitation always succeeds once no
 * live Keycloak invitation remains — even when the person already used the email link and is in the
 * Keycloak org — and Kazi itself refuses them at first sign-in (403 "Access revoked", no member
 * row). A newer pending invitation wins; an existing member is unaffected; a Keycloak error still
 * refuses the revoke (fail closed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitationRevokeAlwaysWinsIntegrationTest {

  private static final String ORG_ID = "org_revoke_always_wins";
  private static final String OWNER = "user_raw_owner";
  private static final String KC_ORG = "kc-org-raw";
  private static final String ACCESS_REVOKED_DETAIL =
      "Your invitation to this firm was revoked. Ask the firm to invite you again.";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PendingInvitationRepository invitationRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;

  @MockitoBean private KeycloakAdminClient keycloakAdminClient;

  private String schema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Revoke Always Wins Attorneys", "legal-za");
    schema = mappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "raw_owner@test.com", "Raw Owner", "owner");
  }

  @Test
  void revokeAfterTheLinkWasUsed_succeeds_andTheirFirstRequestIsRefused() throws Exception {
    // the person registered through the email link: Keycloak added them to the org and deleted its
    // invitation, so the exhaustive lookup finds none — they have no Kazi member row yet
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn(KC_ORG);
    when(keycloakAdminClient.findInvitationIdsByEmail(KC_ORG, "raw_used@test.com"))
        .thenReturn(List.of());
    String id = invite("raw_used@test.com", roleId("member"));

    revoke(id).andExpect(status().isNoContent());
    assertThat(statusOf(id)).isEqualTo(InvitationStatus.REVOKED);
    // decision: Kazi does not remove the person from the Keycloak org (for a member who registered
    // through the link Keycloak would delete the whole account); the sign-in block below is the
    // authority
    verify(keycloakAdminClient, never()).removeOrgMember(any(), any());

    firstRequest("user_raw_used", "raw_used@test.com")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access revoked"))
        .andExpect(jsonPath("$.detail").value(ACCESS_REVOKED_DETAIL));
    assertNoMember("user_raw_used", "raw_used@test.com");

    // every later request too (a refusal is never cached as a member)
    firstRequest("user_raw_used", "raw_used@test.com").andExpect(status().isForbidden());
    assertNoMember("user_raw_used", "raw_used@test.com");
  }

  @Test
  void theRefusalMatchesTheEmailExactlyAsTheInvitationLookupDoes() throws Exception {
    String id = invite("raw_mixed@test.com", roleId("member"));
    revoke(id).andExpect(status().isNoContent());

    firstRequest("user_raw_mixed", "  RAW_Mixed@Test.com ")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access revoked"));
    assertNoMember("user_raw_mixed", "raw_mixed@test.com");
  }

  @Test
  void aReInviteAfterTheRevoke_wins() throws Exception {
    String first = invite("raw_again@test.com", roleId("member"));
    revoke(first).andExpect(status().isNoContent());
    String bookkeeper = createRole("Bookkeeper RAW", "INVOICING");
    invite("raw_again@test.com", bookkeeper);

    firstRequest("user_raw_again", "raw_again@test.com")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Bookkeeper RAW"));
  }

  /**
   * Fix 6 (Keycloak-stack OBS F-2): the revoked person stays in the Keycloak org, so re-inviting
   * them gets Keycloak's 409 "User already a member of the organization" and no email goes out. The
   * invitation is still created (and admits them on their next request), and the 201 says why no
   * email was sent so the inviter can tell them.
   */
  @Test
  void reInvitingSomeoneWhoAlreadyHasALogin_createsTheInvitation_andSaysNoEmailWasSent()
      throws Exception {
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn(KC_ORG);
    when(keycloakAdminClient.findInvitationIdsByEmail(KC_ORG, "raw_login@test.com"))
        .thenReturn(List.of());
    revoke(invite("raw_login@test.com", roleId("member"))).andExpect(status().isNoContent());
    when(keycloakAdminClient.inviteMember(KC_ORG, "raw_login@test.com", "member", null))
        .thenReturn(KeycloakAdminClient.InviteResult.ALREADY_MEMBER);

    mockMvc
        .perform(
            post("/api/invitations")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\": \"raw_login@test.com\", \"orgRoleId\": \"%s\"}"
                        .formatted(roleId("member"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.emailNotSentReason").value("ALREADY_HAS_LOGIN"));

    firstRequest("user_raw_login", "raw_login@test.com")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Member"));
  }

  /**
   * CodeRabbit (MAJOR): an expired re-invite must not lift the revocation. Revoke → re-invite → the
   * re-invite expires (still PENDING past its expiry, or already marked EXPIRED) → the latest
   * non-expired invitation is the revoked one → 403, no member.
   */
  @Test
  void anExpiredReInvite_doesNotLiftTheRevocation() throws Exception {
    revoke(invite("raw_expired_pending@test.com", roleId("member")))
        .andExpect(status().isNoContent());
    saveReInvite("raw_expired_pending@test.com", false);

    firstRequest("user_raw_expired_pending", "raw_expired_pending@test.com")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access revoked"))
        .andExpect(jsonPath("$.detail").value(ACCESS_REVOKED_DETAIL));
    assertNoMember("user_raw_expired_pending", "raw_expired_pending@test.com");
  }

  @Test
  void aReInviteAlreadyMarkedExpired_doesNotLiftTheRevocation() throws Exception {
    revoke(invite("raw_expired_marked@test.com", roleId("member")))
        .andExpect(status().isNoContent());
    saveReInvite("raw_expired_marked@test.com", true);

    firstRequest("user_raw_expired_marked", "raw_expired_marked@test.com")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access revoked"));
    assertNoMember("user_raw_expired_marked", "raw_expired_marked@test.com");
  }

  @Test
  void anExistingMember_isUnaffectedByALaterRevokedInvitation() throws Exception {
    firstRequest("user_raw_member", "raw_member@test.com").andExpect(status().isOk());
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              Member owner = memberRepository.findByClerkUserId(OWNER).orElseThrow();
              var invitation =
                  new PendingInvitation(
                      "raw_member@test.com",
                      orgRoleRepository.findBySlug("member").orElseThrow(),
                      owner,
                      Instant.now().plus(7, ChronoUnit.DAYS));
              invitation.revoke();
              invitationRepository.save(invitation);
            });

    firstRequest("user_raw_member", "raw_member@test.com")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Member"));
  }

  @Test
  void aKeycloakErrorStillRefusesTheRevoke_andTheInvitationStaysPending() throws Exception {
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn(KC_ORG);
    when(keycloakAdminClient.findInvitationIdsByEmail(KC_ORG, "raw_kc_down@test.com"))
        .thenThrow(new InvalidStateException("Keycloak unavailable", "down"));
    String id = invite("raw_kc_down@test.com", roleId("member"));

    revoke(id)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invitation not revoked"));
    assertThat(statusOf(id)).isEqualTo(InvitationStatus.PENDING);
  }

  // --- helpers ---

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private String invite(String email, String roleId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invitations")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"%s\", \"orgRoleId\": \"%s\"}".formatted(email, roleId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private ResultActions revoke(String invitationId) throws Exception {
    return mockMvc.perform(delete("/api/invitations/" + invitationId).with(owner()));
  }

  /** The person's request with a JWT for {@code email} (the JIT member-creation path). */
  private ResultActions firstRequest(String subject, String email) throws Exception {
    return mockMvc.perform(
        get("/api/me/capabilities")
            .with(
                jwt()
                    .jwt(
                        j ->
                            j.subject(subject)
                                .claim("o", Map.of("id", ORG_ID, "rol", "member"))
                                .claim("email", email))));
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

  private String createRole(String name, String capability) throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"%s\", \"description\": \"t\", \"capabilities\": [\"%s\"]}"
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
  }

  private InvitationStatus statusOf(String invitationId) {
    InvitationStatus[] status = new InvitationStatus[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                status[0] =
                    invitationRepository
                        .findById(UUID.fromString(invitationId))
                        .orElseThrow()
                        .getStatus());
    return status[0];
  }

  /**
   * A re-invite whose expiry has passed, saved the way the codebase's expiry tests do (an
   * invitation built with a past {@code expiresAt}): still PENDING, or already marked EXPIRED.
   */
  private void saveReInvite(String email, boolean markedExpired) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              Member owner = memberRepository.findByClerkUserId(OWNER).orElseThrow();
              var reInvite =
                  new PendingInvitation(
                      email,
                      orgRoleRepository.findBySlug("member").orElseThrow(),
                      owner,
                      Instant.now().minus(1, ChronoUnit.DAYS));
              if (markedExpired) {
                reInvite.expire();
              }
              invitationRepository.save(reInvite);
            });
  }

  private void assertNoMember(String subject, String email) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              assertThat(memberRepository.findByClerkUserId(subject)).isEmpty();
              assertThat(memberRepository.existsByEmail(email)).isFalse();
            });
  }
}
