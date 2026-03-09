package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrgRoleServiceTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final UUID ROLE_ID = UUID.randomUUID();

  @Mock private OrgRoleRepository orgRoleRepository;
  @Mock private MemberRepository memberRepository;
  @InjectMocks private OrgRoleService service;

  @Test
  void resolveCapabilities_ownerRole_returnsAll() {
    var member = memberWithIdAndRole(MEMBER_ID, ROLE_ID, Set.of());
    var role = orgRoleWithId(ROLE_ID, "Owner", "owner", true);

    when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    var result = service.resolveCapabilities(MEMBER_ID);

    assertThat(result).containsExactlyInAnyOrderElementsOf(Capability.ALL_NAMES);
  }

  @Test
  void resolveCapabilities_memberRole_returnsEmpty() {
    var member = memberWithIdAndRole(MEMBER_ID, ROLE_ID, Set.of());
    var role = orgRoleWithId(ROLE_ID, "Member", "member", true);
    // System role "member" does NOT get all capabilities (only owner/admin do)
    // and since it has no capabilities assigned, result is empty

    when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    var result = service.resolveCapabilities(MEMBER_ID);

    assertThat(result).isEmpty();
  }

  @Test
  void resolveCapabilities_customRole_returnsRoleCaps() {
    var member = memberWithIdAndRole(MEMBER_ID, ROLE_ID, Set.of());
    var role = orgRoleWithId(ROLE_ID, "Project Lead", "project-lead", false);
    role.setCapabilities(Set.of(Capability.PROJECT_MANAGEMENT, Capability.TEAM_OVERSIGHT));

    when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    var result = service.resolveCapabilities(MEMBER_ID);

    assertThat(result).containsExactlyInAnyOrder("PROJECT_MANAGEMENT", "TEAM_OVERSIGHT");
  }

  @Test
  void resolveCapabilities_withAddOverride_addsCapability() {
    var member = memberWithIdAndRole(MEMBER_ID, ROLE_ID, Set.of("+INVOICING"));
    var role = orgRoleWithId(ROLE_ID, "Project Lead", "project-lead", false);
    role.setCapabilities(Set.of(Capability.PROJECT_MANAGEMENT));

    when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    var result = service.resolveCapabilities(MEMBER_ID);

    assertThat(result).containsExactlyInAnyOrder("PROJECT_MANAGEMENT", "INVOICING");
  }

  @Test
  void resolveCapabilities_withRemoveOverride_removesCapability() {
    var member = memberWithIdAndRole(MEMBER_ID, ROLE_ID, Set.of("-PROJECT_MANAGEMENT"));
    var role = orgRoleWithId(ROLE_ID, "Project Lead", "project-lead", false);
    role.setCapabilities(Set.of(Capability.PROJECT_MANAGEMENT, Capability.TEAM_OVERSIGHT));

    when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    var result = service.resolveCapabilities(MEMBER_ID);

    assertThat(result).containsExactly("TEAM_OVERSIGHT");
  }

  @Test
  void createRole_validRequest_setsSlug() {
    var request =
        new CreateOrgRoleRequest(
            "Project Manager", "Manages projects", Set.of("PROJECT_MANAGEMENT"));

    when(orgRoleRepository.existsByNameIgnoreCase("Project Manager")).thenReturn(false);
    when(orgRoleRepository.save(any(OrgRole.class)))
        .thenAnswer(
            invocation -> {
              OrgRole saved = invocation.getArgument(0);
              // Set ID via reflection to simulate persistence
              var idField = OrgRole.class.getDeclaredField("id");
              idField.setAccessible(true);
              idField.set(saved, UUID.randomUUID());
              return saved;
            });

    var result = service.createRole(request);

    assertThat(result.slug()).isEqualTo("project-manager");
    assertThat(result.name()).isEqualTo("Project Manager");
    assertThat(result.capabilities()).containsExactly("PROJECT_MANAGEMENT");
    assertThat(result.isSystem()).isFalse();
  }

  @Test
  void createRole_duplicateName_throws() {
    var request = new CreateOrgRoleRequest("Owner", "Duplicate", Set.of());

    when(orgRoleRepository.existsByNameIgnoreCase("Owner")).thenReturn(true);

    assertThatThrownBy(() -> service.createRole(request))
        .isInstanceOf(ResourceConflictException.class);

    verify(orgRoleRepository, never()).save(any());
  }

  @Test
  void deleteRole_withMembers_throws() {
    var role = orgRoleWithId(ROLE_ID, "Custom Role", "custom-role", false);

    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
    when(memberRepository.countByOrgRoleId(ROLE_ID)).thenReturn(3L);

    assertThatThrownBy(() -> service.deleteRole(ROLE_ID))
        .isInstanceOf(ResourceConflictException.class);

    verify(orgRoleRepository, never()).delete(any());
  }

  @Test
  void deleteRole_systemRole_throws() {
    var role = orgRoleWithId(ROLE_ID, "Owner", "owner", true);

    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    assertThatThrownBy(() -> service.deleteRole(ROLE_ID)).isInstanceOf(InvalidStateException.class);

    verify(orgRoleRepository, never()).delete(any());
  }

  @Test
  void generateSlug_variousInputs() {
    assertThat(OrgRoleService.generateSlug("Project Manager")).isEqualTo("project-manager");
    assertThat(OrgRoleService.generateSlug("Senior Associate (Level 2)"))
        .isEqualTo("senior-associate-level-2");
    assertThat(OrgRoleService.generateSlug("  Admin  ")).isEqualTo("admin");
    assertThat(OrgRoleService.generateSlug("ONE---TWO")).isEqualTo("one-two");
  }

  // --- Test helpers ---

  private static OrgRole orgRoleWithId(UUID id, String name, String slug, boolean isSystem) {
    var role = new OrgRole(name, slug, name + " description", isSystem);
    try {
      var idField = OrgRole.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(role, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to set role ID", e);
    }
    return role;
  }

  private static Member memberWithIdAndRole(UUID memberId, UUID orgRoleId, Set<String> overrides) {
    var member = new Member("clerk_user", "test@test.com", "Test", null, "member");
    try {
      var idField = Member.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(member, memberId);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to set member ID", e);
    }
    member.setOrgRoleId(orgRoleId);
    member.setCapabilityOverrides(overrides);
    return member;
  }
}
