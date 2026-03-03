package io.b2mash.b2b.b2bstrawman.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.keycloak.dto.KeycloakInvitation;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.KeycloakOrganization;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.ProvisioningException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService.ProvisioningResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrgManagementServiceTest {

  @Mock private KeycloakAdminService keycloakAdminService;
  @Mock private TenantProvisioningService provisioningService;
  @Mock private MemberSyncService memberSyncService;

  private OrgManagementService service;

  @BeforeEach
  void setUp() {
    service =
        new OrgManagementService(keycloakAdminService, provisioningService, memberSyncService);
  }

  @Test
  void createOrganization_success() {
    var orgId = UUID.randomUUID().toString();
    when(keycloakAdminService.createOrganization("My Org", "my-org"))
        .thenReturn(new KeycloakOrganization(orgId, "My Org", "my-org", true));
    when(provisioningService.provisionTenant(orgId, "My Org"))
        .thenReturn(ProvisioningResult.success("tenant_abc123def456"));
    when(memberSyncService.syncMember(
            orgId, "user-123", "user@test.com", "Test User", null, "owner"))
        .thenReturn(new MemberSyncService.SyncResult(UUID.randomUUID(), true));

    var result = service.createOrganization("My Org", "user-123", "user@test.com", "Test User");

    assertThat(result.slug()).isEqualTo("my-org");
    assertThat(result.orgId()).isEqualTo(orgId);

    verify(keycloakAdminService).createOrganization("My Org", "my-org");
    verify(provisioningService).provisionTenant(orgId, "My Org");
    verify(keycloakAdminService).addMember(orgId, "user-123");
    verify(memberSyncService)
        .syncMember(orgId, "user-123", "user@test.com", "Test User", null, "owner");
  }

  @Test
  void createOrganization_provisioningFailure_compensatesWithDelete() {
    var orgId = UUID.randomUUID().toString();
    when(keycloakAdminService.createOrganization("Fail Org", "fail-org"))
        .thenReturn(new KeycloakOrganization(orgId, "Fail Org", "fail-org", true));
    when(provisioningService.provisionTenant(orgId, "Fail Org"))
        .thenThrow(
            new ProvisioningException("DB error", new RuntimeException("connection refused")));

    assertThatThrownBy(
            () -> service.createOrganization("Fail Org", "user-123", "user@test.com", "Test User"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Organization creation failed");

    verify(keycloakAdminService).deleteOrganization(orgId);
    verify(keycloakAdminService, never()).addMember(any(), any());
    verify(memberSyncService, never()).syncMember(any(), any(), any(), any(), any(), any());
  }

  @Test
  void createOrganization_addMemberFailure_compensatesWithDelete() {
    var orgId = UUID.randomUUID().toString();
    when(keycloakAdminService.createOrganization("Fail Org", "fail-org"))
        .thenReturn(new KeycloakOrganization(orgId, "Fail Org", "fail-org", true));
    when(provisioningService.provisionTenant(orgId, "Fail Org"))
        .thenReturn(ProvisioningResult.success("tenant_abc123def456"));
    doThrow(new RuntimeException("Keycloak error"))
        .when(keycloakAdminService)
        .addMember(orgId, "user-123");

    assertThatThrownBy(
            () -> service.createOrganization("Fail Org", "user-123", "user@test.com", "Test User"))
        .isInstanceOf(RuntimeException.class);

    verify(keycloakAdminService).deleteOrganization(orgId);
  }

  @Test
  void listUserOrganizations_mapsFromKeycloak() {
    when(keycloakAdminService.getUserOrganizations("user-123"))
        .thenReturn(
            List.of(
                new KeycloakOrganization("org-1", "Org One", "org-one", true),
                new KeycloakOrganization("org-2", "Org Two", "org-two", true)));

    var result = service.listUserOrganizations("user-123");

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo("org-1");
    assertThat(result.get(0).name()).isEqualTo("Org One");
    assertThat(result.get(0).slug()).isEqualTo("org-one");
    assertThat(result.get(1).slug()).isEqualTo("org-two");
  }

  @Test
  void inviteToOrganization_delegatesToKeycloak() {
    service.inviteToOrganization("org-1", "invited@test.com");

    verify(keycloakAdminService).inviteToOrganization("org-1", "invited@test.com");
  }

  @Test
  void listInvitations_mapsFromKeycloak() {
    when(keycloakAdminService.listInvitations("org-1"))
        .thenReturn(List.of(new KeycloakInvitation("inv-1", "user@test.com", "org-1")));

    var result = service.listInvitations("org-1");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo("inv-1");
    assertThat(result.get(0).email()).isEqualTo("user@test.com");
    assertThat(result.get(0).status()).isEqualTo("PENDING");
  }

  @Test
  void cancelInvitation_delegatesToKeycloak() {
    service.cancelInvitation("org-1", "inv-1");

    verify(keycloakAdminService).cancelInvitation("org-1", "inv-1");
  }

  @Test
  void toSlug_convertsNameCorrectly() {
    assertThat(OrgManagementService.toSlug("My Awesome Org")).isEqualTo("my-awesome-org");
    assertThat(OrgManagementService.toSlug("  Multiple   Spaces  ")).isEqualTo("multiple-spaces");
    assertThat(OrgManagementService.toSlug("Special!@#$Characters")).isEqualTo("specialcharacters");
    assertThat(OrgManagementService.toSlug("Already-Slugged")).isEqualTo("already-slugged");
  }
}
