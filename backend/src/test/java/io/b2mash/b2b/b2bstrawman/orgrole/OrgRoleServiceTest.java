package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.UpdateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.util.List;
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
  @Mock private io.b2mash.b2b.b2bstrawman.audit.AuditService auditService;
  @Mock private io.b2mash.b2b.b2bstrawman.notification.NotificationService notificationService;
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
              if (saved.getId() == null) {
                TestIds.withId(saved, UUID.randomUUID());
              }
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
  }

  @Test
  void deleteRole_withMembers_throws() {
    var role = orgRoleWithId(ROLE_ID, "Custom Role", "custom-role", false);

    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
    when(memberRepository.countByOrgRoleId(ROLE_ID)).thenReturn(3L);

    assertThatThrownBy(() -> service.deleteRole(ROLE_ID))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void deleteRole_systemRole_throws() {
    var role = orgRoleWithId(ROLE_ID, "Owner", "owner", true);

    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    assertThatThrownBy(() -> service.deleteRole(ROLE_ID)).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void createRole_invalidCapability_throwsInvalidState() {
    var request = new CreateOrgRoleRequest("Tester", "Tests things", Set.of("NONEXISTENT_CAP"));

    when(orgRoleRepository.existsByNameIgnoreCase("Tester")).thenReturn(false);

    assertThatThrownBy(() -> service.createRole(request)).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void listRoles_returnsAll() {
    var role1 = orgRoleWithId(UUID.randomUUID(), "Owner", "owner", true);
    var role2 = orgRoleWithId(UUID.randomUUID(), "Custom", "custom", false);

    when(orgRoleRepository.findAll()).thenReturn(List.of(role1, role2));
    when(memberRepository.countByOrgRoleId(role1.getId())).thenReturn(2L);
    when(memberRepository.countByOrgRoleId(role2.getId())).thenReturn(1L);

    var result = service.listRoles();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).memberCount()).isEqualTo(2);
    assertThat(result.get(1).memberCount()).isEqualTo(1);
  }

  @Test
  void getRole_found_returnsResponse() {
    var role = orgRoleWithId(ROLE_ID, "Custom", "custom", false);
    role.setCapabilities(Set.of(Capability.INVOICING));

    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
    when(memberRepository.countByOrgRoleId(ROLE_ID)).thenReturn(5L);

    var result = service.getRole(ROLE_ID);

    assertThat(result.name()).isEqualTo("Custom");
    assertThat(result.memberCount()).isEqualTo(5);
    assertThat(result.capabilities()).containsExactly("INVOICING");
  }

  @Test
  void getRole_notFound_throws() {
    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getRole(ROLE_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void updateRole_validRequest_updatesFields() {
    var role = orgRoleWithId(ROLE_ID, "Old Name", "old-name", false);
    role.setCapabilities(Set.of(Capability.INVOICING));

    var request = new UpdateOrgRoleRequest("New Name", "New desc", Set.of("PROJECT_MANAGEMENT"));

    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
    when(orgRoleRepository.existsByNameIgnoreCaseAndIdNot("New Name", ROLE_ID)).thenReturn(false);
    when(orgRoleRepository.save(any(OrgRole.class))).thenAnswer(inv -> inv.getArgument(0));
    when(memberRepository.countByOrgRoleId(ROLE_ID)).thenReturn(0L);

    var result = service.updateRole(ROLE_ID, request);

    assertThat(result.name()).isEqualTo("New Name");
    assertThat(result.slug()).isEqualTo("new-name");
    assertThat(result.description()).isEqualTo("New desc");
    assertThat(result.capabilities()).containsExactly("PROJECT_MANAGEMENT");
  }

  @Test
  void updateRole_systemRole_throws() {
    var role = orgRoleWithId(ROLE_ID, "Owner", "owner", true);

    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    assertThatThrownBy(() -> service.updateRole(ROLE_ID, new UpdateOrgRoleRequest("X", null, null)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void updateRole_duplicateName_throws() {
    var role = orgRoleWithId(ROLE_ID, "Old Name", "old-name", false);

    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
    when(orgRoleRepository.existsByNameIgnoreCaseAndIdNot("Taken Name", ROLE_ID)).thenReturn(true);

    assertThatThrownBy(
            () -> service.updateRole(ROLE_ID, new UpdateOrgRoleRequest("Taken Name", null, null)))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void updateRole_invalidCapability_throwsInvalidState() {
    var role = orgRoleWithId(ROLE_ID, "Custom", "custom", false);

    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    assertThatThrownBy(
            () ->
                service.updateRole(ROLE_ID, new UpdateOrgRoleRequest(null, null, Set.of("BOGUS"))))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void resolveCapabilities_invalidOverride_skipped() {
    var member = memberWithIdAndRole(MEMBER_ID, ROLE_ID, Set.of("+NONEXISTENT_CAP"));
    var role = orgRoleWithId(ROLE_ID, "Custom", "custom", false);
    role.setCapabilities(Set.of(Capability.INVOICING));

    when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

    var result = service.resolveCapabilities(MEMBER_ID);

    assertThat(result).containsExactly("INVOICING");
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
    return TestIds.withId(role, id);
  }

  private static Member memberWithIdAndRole(UUID memberId, UUID orgRoleId, Set<String> overrides) {
    var member = new Member("clerk_user", "test@test.com", "Test", null);
    TestIds.withId(member, memberId);
    member.setOrgRoleId(orgRoleId);
    member.setCapabilityOverrides(overrides);
    return member;
  }
}
