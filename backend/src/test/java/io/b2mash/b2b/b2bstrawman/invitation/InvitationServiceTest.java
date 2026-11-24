package io.b2mash.b2b.b2bstrawman.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.invitation.dto.InvitationDtos.CreateInvitationRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitationServiceTest {

  private static final String ORG_ID = "org_inv_svc_test";

  @Autowired private InvitationService invitationService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.member.MemberRepository memberRepository;
  @Autowired private PendingInvitationRepository invitationRepository;

  @MockitoBean private KeycloakAdminClient keycloakAdminClient;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID adminRoleId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Inv Svc Test Org", null);

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a member in the tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var ownerRole = orgRoleRepository.findBySlug("owner").orElseThrow();
              var member =
                  new io.b2mash.b2b.b2bstrawman.member.Member(
                      "user_inv_svc_owner", "inv_svc_owner@test.com", "Owner", null, ownerRole);
              member = memberRepository.save(member);
              ownerMemberId = member.getId();
              adminRoleId = orgRoleRepository.findBySlug("admin").orElseThrow().getId();
            });
  }

  @Test
  void createInvitation_callsKeycloakInvite() {
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn("kc-org-uuid");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner") // the Admin role is invited by an owner (Task C)
        .run(
            () -> {
              var request = new CreateInvitationRequest("kc-test1@example.com", adminRoleId);
              invitationService.createInvitation(request, ownerMemberId);

              verify(keycloakAdminClient).resolveOrgId(ORG_ID);
              verify(keycloakAdminClient)
                  .inviteMember(eq("kc-org-uuid"), eq("kc-test1@example.com"), eq("admin"), any());
            });
  }

  @Test
  void createInvitation_keycloakFails_invitationStillCreated() {
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn("kc-org-uuid");
    doThrow(new RuntimeException("KC unavailable"))
        .when(keycloakAdminClient)
        .inviteMember(any(), any(), any(), any());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner") // the Admin role is invited by an owner (Task C)
        .run(
            () -> {
              var request = new CreateInvitationRequest("kc-test2@example.com", adminRoleId);
              var response = invitationService.createInvitation(request, ownerMemberId);

              // Invitation should still be created despite KC failure
              assertThat(response).isNotNull();
              assertThat(response.email()).isEqualTo("kc-test2@example.com");
            });
  }

  @Test
  void createInvitation_noOrgContext_skipsKeycloak() {
    // When no ORG_ID is bound, KC invite should not be called
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner") // the Admin role is invited by an owner (Task C)
        .run(
            () -> {
              var request = new CreateInvitationRequest("kc-test3@example.com", adminRoleId);
              var response = invitationService.createInvitation(request, ownerMemberId);

              assertThat(response).isNotNull();
              verify(keycloakAdminClient, never()).resolveOrgId(any());
              verify(keycloakAdminClient, never()).inviteMember(any(), any(), any(), any());
            });
  }

  // --- FR-8: revoking an invitation revokes the Keycloak invitation (its email link) too ---
  // Keycloak 26.5 persists an organization invitation at invite-user and checks it still exists
  // when the emailed link is used (registration and existing-user paths); membership is added only
  // when the link is accepted, and the Keycloak invitation is deleted then.

  @Test
  void revokeInvitation_revokesTheKeycloakInvitationForThatEmail() {
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn("kc-org-uuid");
    when(keycloakAdminClient.listInvitations("kc-org-uuid"))
        .thenReturn(
            List.of(
                Map.of("id", "kc-inv-other", "email", "other-kc-revoke1@example.com"),
                Map.of("id", "kc-inv-1", "email", "kc-revoke1@example.com")));

    UUID id = invite("kc-revoke1@example.com");
    inOwnerScope(() -> invitationService.revokeInvitation(id));

    verify(keycloakAdminClient).revokeInvitation("kc-org-uuid", "kc-inv-1");
    verify(keycloakAdminClient, never()).revokeInvitation("kc-org-uuid", "kc-inv-other");
    assertThat(status(id)).isEqualTo(InvitationStatus.REVOKED);
  }

  @Test
  void revokeInvitation_keycloakRevokeFails_refusedAndKaziInvitationStaysPending() {
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn("kc-org-uuid");
    when(keycloakAdminClient.listInvitations("kc-org-uuid"))
        .thenReturn(List.of(Map.of("id", "kc-inv-2", "email", "kc-revoke2@example.com")));
    doThrow(new InvalidStateException("Keycloak unavailable", "Keycloak Admin API unavailable"))
        .when(keycloakAdminClient)
        .revokeInvitation("kc-org-uuid", "kc-inv-2");

    UUID id = invite("kc-revoke2@example.com");
    var refused =
        catchThrowableOfType(
            InvalidStateException.class,
            () -> inOwnerScope(() -> invitationService.revokeInvitation(id)));

    assertThat(refused).isNotNull();
    assertThat(refused.getBody().getStatus()).isEqualTo(400);
    assertThat(refused.getBody().getTitle()).isEqualTo("Invitation not revoked");
    assertThat(refused.getBody().getDetail())
        .isEqualTo(
            "The invitation email for 'kc-revoke2@example.com' could not be cancelled in Keycloak,"
                + " so its link may still work. Nothing was changed. Please try again.");
    assertThat(status(id)).isEqualTo(InvitationStatus.PENDING);
  }

  @Test
  void revokeInvitation_keycloakUnreachable_refusedAndKaziInvitationStaysPending() {
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn("kc-org-uuid");
    when(keycloakAdminClient.listInvitations("kc-org-uuid"))
        .thenThrow(new RuntimeException("connection refused"));

    UUID id = invite("kc-revoke3@example.com");
    var refused =
        catchThrowableOfType(
            InvalidStateException.class,
            () -> inOwnerScope(() -> invitationService.revokeInvitation(id)));

    assertThat(refused).isNotNull();
    assertThat(refused.getBody().getTitle()).isEqualTo("Invitation not revoked");
    assertThat(status(id)).isEqualTo(InvitationStatus.PENDING);
  }

  @Test
  void revokeInvitation_linkAlreadyAcceptedInKeycloak_refusedWithConflict() {
    // the person used the link (now a Keycloak org member, Keycloak deleted its invitation) but has
    // not signed in to Kazi yet: revoking only the Kazi invitation would let them join as Member
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn("kc-org-uuid");
    when(keycloakAdminClient.listInvitations("kc-org-uuid")).thenReturn(List.of());
    when(keycloakAdminClient.findUserByEmail("kc-revoke4@example.com"))
        .thenReturn(Optional.of("kc-user-4"));
    when(keycloakAdminClient.listOrgMemberIds("kc-org-uuid"))
        .thenReturn(List.of("kc-user-x", "kc-user-4"));

    UUID id = invite("kc-revoke4@example.com");
    var refused =
        catchThrowableOfType(
            ResourceConflictException.class,
            () -> inOwnerScope(() -> invitationService.revokeInvitation(id)));

    assertThat(refused).isNotNull();
    assertThat(refused.getBody().getStatus()).isEqualTo(409);
    assertThat(refused.getBody().getTitle()).isEqualTo("Invitation already accepted");
    assertThat(refused.getBody().getDetail())
        .isEqualTo(
            "'kc-revoke4@example.com' has already accepted the invitation email and joins with"
                + " the invited role on their first sign-in, so it can no longer be revoked."
                + " Remove them from the team once they appear.");
    assertThat(status(id)).isEqualTo(InvitationStatus.PENDING);
    verify(keycloakAdminClient, never()).revokeInvitation(any(), any());
  }

  @Test
  void revokeInvitation_noKeycloakInvitationAndNotAMember_revokesInKazi() {
    // e.g. the Keycloak invite-user call failed when the invitation was created (best-effort)
    when(keycloakAdminClient.resolveOrgId(ORG_ID)).thenReturn("kc-org-uuid");
    when(keycloakAdminClient.listInvitations("kc-org-uuid")).thenReturn(List.of());
    when(keycloakAdminClient.findUserByEmail("kc-revoke5@example.com"))
        .thenReturn(Optional.empty());

    UUID id = invite("kc-revoke5@example.com");
    inOwnerScope(() -> invitationService.revokeInvitation(id));

    assertThat(status(id)).isEqualTo(InvitationStatus.REVOKED);
    verify(keycloakAdminClient, never()).revokeInvitation(any(), any());
  }

  @Test
  void revokeInvitation_noOrgContext_skipsKeycloak() {
    UUID id = invite("kc-revoke6@example.com");
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> invitationService.revokeInvitation(id));

    assertThat(status(id)).isEqualTo(InvitationStatus.REVOKED);
    verify(keycloakAdminClient, never()).listInvitations(any());
    verify(keycloakAdminClient, never()).revokeInvitation(any(), any());
  }

  // --- helpers ---

  /** Creates a pending invitation into the Member role (Keycloak invite-user mocked). */
  private UUID invite(String email) {
    UUID[] id = new UUID[1];
    inOwnerScope(
        () -> {
          UUID memberRoleId = orgRoleRepository.findBySlug("member").orElseThrow().getId();
          id[0] =
              invitationService
                  .createInvitation(new CreateInvitationRequest(email, memberRoleId), ownerMemberId)
                  .id();
        });
    return id[0];
  }

  private void inOwnerScope(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private InvitationStatus status(UUID invitationId) {
    InvitationStatus[] status = new InvitationStatus[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                status[0] = invitationRepository.findById(invitationId).orElseThrow().getStatus());
    return status[0];
  }
}
