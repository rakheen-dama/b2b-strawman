package io.b2mash.b2b.b2bstrawman.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.invitation.InvitationStatus;
import io.b2mash.b2b.b2bstrawman.invitation.PendingInvitation;
import io.b2mash.b2b.b2bstrawman.invitation.PendingInvitationRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Integration tests verifying that MemberFilter resolves roles from pending invitations when
 * lazy-creating members.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberFilterInvitationTest {

  private static final String ORG_ID = "org_inv_flow_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private PendingInvitationRepository invitationRepository;

  private String schemaName;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invitation Flow Test Org", null);
    schemaName =
        mappingRepository
            .findByClerkOrgId(ORG_ID)
            .map(OrgSchemaMapping::getSchemaName)
            .orElseThrow();

    // Create an owner member to use as invitedBy
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_inv_flow_owner", "inv_flow_owner@test.com", "Inv Owner", "owner");
  }

  @Test
  void shouldAssignInvitationRoleOnFirstLogin() throws Exception {
    // Create a pending invitation for admin role
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              OrgRole adminRole = orgRoleRepository.findBySlug("admin").orElseThrow();
              Member owner =
                  memberRepository.findByClerkUserId("user_inv_flow_owner").orElseThrow();
              var invitation =
                  new PendingInvitation(
                      "invited_admin@test.com",
                      adminRole,
                      owner,
                      Instant.now().plus(7, ChronoUnit.DAYS));
              invitationRepository.save(invitation);
            });

    // User logs in for the first time with the invited email
    mockMvc
        .perform(
            get("/api/matters")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_invited_admin")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member"))
                                    .claim("email", "invited_admin@test.com"))))
        .andExpect(status().isOk());

    // Verify the member was created with the invitation's admin role
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_invited_admin");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("admin");
            });
  }

  @Test
  void shouldMarkInvitationAsAcceptedAfterMemberCreation() throws Exception {
    // Create a pending invitation
    UUID[] invitationId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              OrgRole memberRole = orgRoleRepository.findBySlug("member").orElseThrow();
              Member owner =
                  memberRepository.findByClerkUserId("user_inv_flow_owner").orElseThrow();
              var invitation =
                  new PendingInvitation(
                      "invited_accepted@test.com",
                      memberRole,
                      owner,
                      Instant.now().plus(7, ChronoUnit.DAYS));
              invitation = invitationRepository.save(invitation);
              invitationId[0] = invitation.getId();
            });

    // User logs in
    mockMvc
        .perform(
            get("/api/matters")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_invited_accepted")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member"))
                                    .claim("email", "invited_accepted@test.com"))))
        .andExpect(status().isOk());

    // Verify the invitation is now ACCEPTED
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var invitation = invitationRepository.findById(invitationId[0]);
              assertThat(invitation).isPresent();
              assertThat(invitation.get().getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            });
  }

  @Test
  void shouldFallbackToMemberRoleWithExpiredInvitation() throws Exception {
    // Create an expired invitation
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              OrgRole adminRole = orgRoleRepository.findBySlug("admin").orElseThrow();
              Member owner =
                  memberRepository.findByClerkUserId("user_inv_flow_owner").orElseThrow();
              var invitation =
                  new PendingInvitation(
                      "invited_expired@test.com",
                      adminRole,
                      owner,
                      Instant.now().minus(1, ChronoUnit.DAYS)); // Already expired
              invitationRepository.save(invitation);
            });

    // User logs in with an expired invitation
    mockMvc
        .perform(
            get("/api/matters")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_invited_expired")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member"))
                                    .claim("email", "invited_expired@test.com"))))
        .andExpect(status().isOk());

    // Verify the member gets default "member" role, not "admin" from expired invitation
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_invited_expired");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("member");
            });
  }

  @Test
  void shouldGetDefaultMemberRoleWithoutInvitation() throws Exception {
    // User logs in without any invitation
    mockMvc
        .perform(
            get("/api/matters")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_no_invitation")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "admin"))
                                    .claim("email", "no_invitation@test.com"))))
        .andExpect(status().isOk());

    // Verify the member gets default "member" role
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_no_invitation");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("member");
            });
  }

  // --- custom-role invitations (fix plan option A: the invitation's stored role id) ---

  @Test
  void shouldAssignCustomInvitationRoleOnFirstLogin() throws Exception {
    UUID[] ids = customRoleInvitation("Partner Inv", "invited_partner@test.com", 7);

    mockMvc
        .perform(
            get("/api/me/capabilities")
                .with(clerkJwt("user_invited_partner", "invited_partner@test.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Partner Inv"))
        .andExpect(jsonPath("$.capabilities", Matchers.containsInAnyOrder(PARTNER_CAPABILITIES)))
        .andExpect(jsonPath("$.capabilities.length()").value(2))
        .andExpect(jsonPath("$.isAdmin").value(false))
        .andExpect(jsonPath("$.isOwner").value(false));

    assertMemberOnRoleAndInvitationAccepted("user_invited_partner", ids);
  }

  @Test
  void shouldAssignCustomInvitationRoleWithKeycloakFormatJwt() throws Exception {
    UUID[] ids = customRoleInvitation("Partner Inv KC", "invited_partner_kc@test.com", 7);

    // Keycloak path: the org arrives in the "organization" claim, there is no Clerk "o" claim
    mockMvc
        .perform(
            get("/api/me/capabilities")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_invited_partner_kc")
                                    .claim("organization", List.of(ORG_ID))
                                    .claim("email", "invited_partner_kc@test.com"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Partner Inv KC"))
        .andExpect(jsonPath("$.capabilities", Matchers.containsInAnyOrder(PARTNER_CAPABILITIES)));

    assertMemberOnRoleAndInvitationAccepted("user_invited_partner_kc", ids);
  }

  @Test
  void customInviteeIgnoresTokenRoleClaims() throws Exception {
    UUID[] ids = customRoleInvitation("Partner Inv Claims", "invited_partner_claims@test.com", 7);

    mockMvc
        .perform(
            get("/api/me/capabilities")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_invited_partner_claims")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "owner"))
                                    .claim("role", "owner")
                                    .claim("org_role", "owner")
                                    .claim("email", "invited_partner_claims@test.com"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Partner Inv Claims"))
        .andExpect(jsonPath("$.isOwner").value(false))
        .andExpect(jsonPath("$.isAdmin").value(false));

    assertMemberOnRoleAndInvitationAccepted("user_invited_partner_claims", ids);
  }

  @Test
  void shouldFallbackToMemberRoleWithExpiredCustomInvitation() throws Exception {
    customRoleInvitation("Partner Inv Expired", "invited_partner_expired@test.com", -1);

    mockMvc
        .perform(
            get("/api/me/capabilities")
                .with(clerkJwt("user_invited_partner_expired", "invited_partner_expired@test.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Member"));

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () ->
                assertThat(
                        memberRepository
                            .findByClerkUserId("user_invited_partner_expired")
                            .orElseThrow()
                            .getOrgRole())
                    .isEqualTo("member"));
  }

  /**
   * A custom role cannot be deleted while a pending invitation offers it (the invitation's FK would
   * refuse the delete anyway — this turns that into a clear 409 instead of a 500), so the invitee
   * still lands on the exact role the inviter chose.
   */
  @Test
  void aCustomRoleOfferedByAPendingInvitationCannotBeDeleted() throws Exception {
    UUID[] ids = customRoleInvitation("Partner Inv Delete", "invited_partner_delete@test.com", 7);

    mockMvc
        .perform(
            delete("/api/org-roles/" + ids[0])
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_flow_owner")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").value("Role in use"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Cannot delete role 'Partner Inv Delete' because 1 invitation(s) reference it."));

    mockMvc
        .perform(
            get("/api/me/capabilities")
                .with(clerkJwt("user_invited_partner_delete", "invited_partner_delete@test.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Partner Inv Delete"));

    assertMemberOnRoleAndInvitationAccepted("user_invited_partner_delete", ids);
  }

  /**
   * CodeRabbit (minor): any invitation referencing a role — revoked, accepted or expired too —
   * blocks its deletion through the {@code pending_invitations_org_role_id_fkey} FK; the guard
   * answers a clear 409 for all of them instead of the FK's 500.
   */
  @Test
  void aCustomRoleReferencedOnlyByARevokedInvitationCannotBeDeleted() throws Exception {
    UUID[] ids = customRoleInvitation("Partner Inv Revoked", "invited_partner_revoked@test.com", 7);
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var invitation = invitationRepository.findById(ids[1]).orElseThrow();
              invitation.revoke();
              invitationRepository.save(invitation);
            });

    mockMvc
        .perform(
            delete("/api/org-roles/" + ids[0])
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_flow_owner")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").value("Role in use"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Cannot delete role 'Partner Inv Revoked' because 1 invitation(s) reference"
                        + " it."));
  }

  // --- helpers ---

  /** A role holding an owner-only capability (SIGN_OFF_TRUST_TAKE_ON) plus VIEW_TRUST. */
  private static final String[] PARTNER_CAPABILITIES = {"VIEW_TRUST", "SIGN_OFF_TRUST_TAKE_ON"};

  /**
   * Saves a custom role {@code name} holding {@link #PARTNER_CAPABILITIES} and a pending invitation
   * into it for {@code email} that expires in {@code expiresInDays}. Returns {role id, invitation
   * id}.
   */
  private UUID[] customRoleInvitation(String name, String email, int expiresInDays) {
    UUID[] ids = new UUID[2];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var role =
                  new OrgRole(
                      name, name.toLowerCase().replace(' ', '-'), "Custom invite role", false);
              role.setCapabilities(
                  Set.of(Capability.VIEW_TRUST, Capability.SIGN_OFF_TRUST_TAKE_ON));
              role = orgRoleRepository.save(role);
              Member owner =
                  memberRepository.findByClerkUserId("user_inv_flow_owner").orElseThrow();
              var invitation =
                  invitationRepository.save(
                      new PendingInvitation(
                          email, role, owner, Instant.now().plus(expiresInDays, ChronoUnit.DAYS)));
              ids[0] = role.getId();
              ids[1] = invitation.getId();
            });
    return ids;
  }

  private void assertMemberOnRoleAndInvitationAccepted(String subject, UUID[] ids) {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId(subject).orElseThrow();
              assertThat(member.getOrgRoleId()).isEqualTo(ids[0]);
              assertThat(invitationRepository.findById(ids[1]).orElseThrow().getStatus())
                  .isEqualTo(InvitationStatus.ACCEPTED);
            });
  }

  private static RequestPostProcessor clerkJwt(String subject, String email) {
    return jwt()
        .jwt(
            j ->
                j.subject(subject)
                    .claim("o", Map.of("id", ORG_ID, "rol", "member"))
                    .claim("email", email));
  }
}
