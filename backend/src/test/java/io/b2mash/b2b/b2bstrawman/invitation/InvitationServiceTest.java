package io.b2mash.b2b.b2bstrawman.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.invitation.dto.InvitationDtos.CreateInvitationRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
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
        .run(
            () -> {
              var request = new CreateInvitationRequest("kc-test3@example.com", adminRoleId);
              var response = invitationService.createInvitation(request, ownerMemberId);

              assertThat(response).isNotNull();
              verify(keycloakAdminClient, never()).resolveOrgId(any());
              verify(keycloakAdminClient, never()).inviteMember(any(), any(), any(), any());
            });
  }
}
