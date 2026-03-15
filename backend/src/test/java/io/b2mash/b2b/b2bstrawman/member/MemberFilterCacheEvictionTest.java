package io.b2mash.b2b.b2bstrawman.member;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.AssignRoleRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.UpdateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests verifying cache eviction behavior in OrgRoleService when roles are assigned or
 * updated.
 */
@ExtendWith(MockitoExtension.class)
class MemberFilterCacheEvictionTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final UUID ROLE_ID = UUID.randomUUID();
  private static final UUID NEW_ROLE_ID = UUID.randomUUID();
  private static final String TENANT_ID = "tenant_cache_eviction_test";
  private static final String CLERK_USER_ID = "clerk_cache_test";

  @Mock private OrgRoleRepository orgRoleRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private AuditService auditService;
  @Mock private NotificationService notificationService;
  private MemberFilter memberFilter;
  private OrgRoleService service;

  @BeforeEach
  void setup() {
    memberFilter = spy(new MemberFilter(memberRepository, null, null));
    service =
        new OrgRoleService(
            orgRoleRepository, memberRepository, auditService, notificationService, memberFilter);
  }

  @Test
  void assignRole_shouldEvictMemberCache() {
    var member = memberWithIdAndRole(MEMBER_ID, ROLE_ID, CLERK_USER_ID, "member");
    var newRole = orgRoleWithId(NEW_ROLE_ID, "Admin", "admin", true);

    org.mockito.Mockito.when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    org.mockito.Mockito.when(orgRoleRepository.findById(NEW_ROLE_ID))
        .thenReturn(Optional.of(newRole));
    org.mockito.Mockito.when(memberRepository.save(member)).thenReturn(member);

    // Run within RequestScopes to provide TENANT_ID and MEMBER_ID + ORG_ROLE for owner check
    ScopedValue.where(io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.TENANT_ID, TENANT_ID)
        .where(io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.ORG_ROLE, "owner")
        .where(
            io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.CAPABILITIES,
            Set.copyOf(Capability.ALL_NAMES))
        .run(
            () -> {
              try {
                service.assignRole(MEMBER_ID, new AssignRoleRequest(NEW_ROLE_ID, Set.of()));
              } catch (ForbiddenException e) {
                // Admin system role can only be assigned by owners — we are owner
                throw new RuntimeException(e);
              }
            });

    verify(memberFilter).evictFromCache(TENANT_ID, CLERK_USER_ID);
  }

  @Test
  void updateRole_withCapabilityChanges_shouldEvictAllAffectedMembers() {
    var role = orgRoleWithId(ROLE_ID, "Custom", "custom", false);
    role.setCapabilities(Set.of(Capability.INVOICING));

    var member1 = memberWithIdAndRole(UUID.randomUUID(), ROLE_ID, "user_1", "member");
    var member2 = memberWithIdAndRole(UUID.randomUUID(), ROLE_ID, "user_2", "member");

    org.mockito.Mockito.when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
    org.mockito.Mockito.when(memberRepository.findByOrgRoleEntity_Id(ROLE_ID))
        .thenReturn(List.of(member1, member2));
    org.mockito.Mockito.when(orgRoleRepository.save(role)).thenReturn(role);
    org.mockito.Mockito.when(memberRepository.countByOrgRoleEntity_Id(ROLE_ID)).thenReturn(2L);

    ScopedValue.where(io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.TENANT_ID, TENANT_ID)
        .run(
            () ->
                service.updateRole(
                    ROLE_ID, new UpdateOrgRoleRequest(null, null, Set.of("PROJECT_MANAGEMENT"))));

    verify(memberFilter).evictFromCache(TENANT_ID, "user_1");
    verify(memberFilter).evictFromCache(TENANT_ID, "user_2");
  }

  @Test
  void updateRole_withoutCapabilityChanges_shouldNotEvictCache() {
    var role = orgRoleWithId(ROLE_ID, "Custom", "custom", false);
    role.setCapabilities(Set.of(Capability.INVOICING));

    org.mockito.Mockito.when(orgRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
    org.mockito.Mockito.when(orgRoleRepository.save(role)).thenReturn(role);
    org.mockito.Mockito.when(memberRepository.countByOrgRoleEntity_Id(ROLE_ID)).thenReturn(0L);

    ScopedValue.where(io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.TENANT_ID, TENANT_ID)
        .run(
            () ->
                service.updateRole(
                    ROLE_ID, new UpdateOrgRoleRequest("New Name", "New desc", null)));

    // No cache eviction because capabilities didn't change
    verify(memberFilter, never())
        .evictFromCache(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  private static OrgRole orgRoleWithId(UUID id, String name, String slug, boolean isSystem) {
    var role = new OrgRole(name, slug, name + " description", isSystem);
    return TestIds.withId(role, id);
  }

  private static Member memberWithIdAndRole(
      UUID memberId, UUID orgRoleId, String clerkUserId, String orgRole) {
    var role = orgRoleWithId(orgRoleId, orgRole, orgRole, true);
    var member = new Member(clerkUserId, "test@test.com", "Test", null, role);
    TestIds.withId(member, memberId);
    member.setCapabilityOverrides(Set.of());
    return member;
  }
}
