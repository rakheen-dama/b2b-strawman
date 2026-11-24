package io.b2mash.b2b.b2bstrawman.orgrole;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invitation.InvitationStatus;
import io.b2mash.b2b.b2bstrawman.invitation.PendingInvitationRepository;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberFilter;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.AssignRoleRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.MemberCapabilitiesResponse;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.MyCapabilitiesResponse;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.OrgRoleResponse;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.UpdateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgRoleService {

  private static final Logger log = LoggerFactory.getLogger(OrgRoleService.class);

  private final OrgRoleRepository orgRoleRepository;
  private final MemberRepository memberRepository;
  private final AuditService auditService;
  private final NotificationService notificationService;
  private final MemberFilter memberFilter;
  private final PendingInvitationRepository invitationRepository;

  public OrgRoleService(
      OrgRoleRepository orgRoleRepository,
      MemberRepository memberRepository,
      AuditService auditService,
      NotificationService notificationService,
      @Lazy MemberFilter memberFilter,
      PendingInvitationRepository invitationRepository) {
    this.orgRoleRepository = orgRoleRepository;
    this.memberRepository = memberRepository;
    this.auditService = auditService;
    this.notificationService = notificationService;
    this.memberFilter = memberFilter;
    this.invitationRepository = invitationRepository;
  }

  @Transactional(readOnly = true)
  public Set<String> resolveCapabilities(UUID memberId) {
    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    return effectiveCapabilities(member.getOrgRoleEntity(), member.getCapabilityOverrides());
  }

  /**
   * The capabilities a member on {@code role} with {@code overrides} holds: the system Owner role
   * all of them, the system Admin role all but the owner-only ones (overrides ignored for both), a
   * custom or Member role its own capabilities with the {@code +CAP}/{@code -CAP} overrides
   * applied.
   */
  private static Set<String> effectiveCapabilities(OrgRole role, Set<String> overrides) {
    if (role == null) {
      return Collections.emptySet();
    }

    if (role.isSystem() && "owner".equals(role.getSlug())) {
      return Set.copyOf(Capability.ALL_NAMES);
    }
    if (role.isSystem() && "admin".equals(role.getSlug())) {
      // Admin gets all capabilities EXCEPT owner-only ones (e.g., destructive compliance actions)
      var adminCaps = new HashSet<>(Capability.ALL_NAMES);
      adminCaps.removeAll(Capability.OWNER_ONLY);
      return Set.copyOf(adminCaps);
    }

    Set<String> effective =
        role.getCapabilities().stream().map(Capability::name).collect(Collectors.toSet());
    effective = new HashSet<>(effective);

    // order-independent: every "+" first, then every "-" — so "-" wins on a contradictory pair
    // (stored before the validation refused one), whatever the set's iteration order
    effective.addAll(overrideCapabilities(overrides, "+"));
    effective.removeAll(overrideCapabilities(overrides, "-"));
    return effective;
  }

  /** The known capabilities named by the {@code sign}-prefixed overrides (unknown ones skipped). */
  private static Set<String> overrideCapabilities(Set<String> overrides, String sign) {
    return overrides.stream()
        .filter(o -> o.length() > 1 && o.startsWith(sign))
        .map(o -> o.substring(1))
        .filter(Capability.ALL_NAMES::contains) // forward-compatibility
        .collect(Collectors.toSet());
  }

  @Transactional(readOnly = true)
  public MyCapabilitiesResponse getMyCapabilities(UUID memberId) {
    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    String roleName =
        member.getOrgRoleEntity() != null ? member.getOrgRoleEntity().getName() : null;

    Set<String> capabilities = RequestScopes.getCapabilities();
    String orgRole = RequestScopes.getOrgRole();
    boolean isAdmin = "admin".equals(orgRole);
    boolean isOwner = "owner".equals(orgRole);

    return new MyCapabilitiesResponse(capabilities, roleName, isAdmin, isOwner, memberId);
  }

  @Transactional(readOnly = true)
  public MemberCapabilitiesResponse getMemberCapabilities(UUID memberId) {
    UUID callerId = RequestScopes.requireMemberId();
    String callerRole = RequestScopes.getOrgRole();
    boolean isSelf = callerId.equals(memberId);
    boolean isAdminOrOwner = "admin".equals(callerRole) || "owner".equals(callerRole);
    if (!isSelf && !isAdminOrOwner) {
      throw new ForbiddenException(
          "Capability access denied",
          "You can only view your own capabilities or must be an admin/owner");
    }

    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    String roleName = null;
    Set<String> roleCapabilities = Collections.emptySet();

    var role = member.getOrgRoleEntity();
    if (role != null) {
      roleName = role.getName();
      roleCapabilities =
          role.getCapabilities().stream().map(Capability::name).collect(Collectors.toSet());
    }

    Set<String> effectiveCapabilities = resolveCapabilities(memberId);

    return new MemberCapabilitiesResponse(
        memberId,
        roleName,
        roleCapabilities,
        // a copy read inside the transaction: the entity's collection is lazy, and for the system
        // owner/admin roles nothing above has loaded it (open-in-view is off)
        Set.copyOf(member.getCapabilityOverrides()),
        effectiveCapabilities);
  }

  @Transactional(readOnly = true)
  public List<OrgRoleResponse> listRoles() {
    return orgRoleRepository.findAll().stream()
        .map(
            role ->
                OrgRoleResponse.from(role, memberRepository.countByOrgRoleEntity_Id(role.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public OrgRoleResponse getRole(UUID id) {
    var role =
        orgRoleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", id));
    return OrgRoleResponse.from(role, memberRepository.countByOrgRoleEntity_Id(id));
  }

  @Transactional
  public OrgRoleResponse createRole(CreateOrgRoleRequest request) {
    if (orgRoleRepository.existsByNameIgnoreCase(request.name())) {
      throw new ResourceConflictException(
          "Role name conflict", "A role with the name '" + request.name() + "' already exists");
    }

    validateCapabilities(request.capabilities());
    if (request.capabilities() != null) {
      requireOwnerToGrant(request.capabilities(), "create a role holding an owner-only capability");
    }

    String slug = generateSlug(request.name());
    var role = new OrgRole(request.name(), slug, request.description(), false);

    if (request.capabilities() != null) {
      role.setCapabilities(
          request.capabilities().stream().map(Capability::valueOf).collect(Collectors.toSet()));
    }

    role = orgRoleRepository.save(role);
    auditService.log(AuditEventBuilder.roleCreated(role));
    return OrgRoleResponse.from(role, 0);
  }

  @Transactional
  public OrgRoleResponse updateRole(UUID id, UpdateOrgRoleRequest request) {
    // locked: the added capabilities are computed from what the role holds now (FR-4)
    var role =
        orgRoleRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", id));

    if (role.isSystem()) {
      throw new InvalidStateException(
          "System role modification", "System roles cannot be modified");
    }

    if (request.name() != null) {
      if (orgRoleRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
        throw new ResourceConflictException(
            "Role name conflict", "A role with the name '" + request.name() + "' already exists");
      }
      role.setName(request.name());
      role.setSlug(generateSlug(request.name()));
    }

    if (request.description() != null) {
      role.setDescription(request.description());
    }

    Set<String> addedCaps = Set.of();
    Set<String> removedCaps = Set.of();
    boolean capsChanged = false;

    if (request.capabilities() != null) {
      validateCapabilities(request.capabilities());
      Set<String> beforeCaps =
          role.getCapabilities().stream().map(Capability::name).collect(Collectors.toSet());
      Set<String> afterCaps = new HashSet<>(request.capabilities());
      addedCaps =
          afterCaps.stream().filter(c -> !beforeCaps.contains(c)).collect(Collectors.toSet());
      removedCaps =
          beforeCaps.stream().filter(c -> !afterCaps.contains(c)).collect(Collectors.toSet());
      capsChanged = !addedCaps.isEmpty() || !removedCaps.isEmpty();
      // removing an owner-only capability, or keeping one, stays open to admins (FR-4)
      requireOwnerToGrant(addedCaps, "add an owner-only capability to a role");
      role.setCapabilities(
          request.capabilities().stream().map(Capability::valueOf).collect(Collectors.toSet()));
    }

    List<Member> affectedMembers =
        capsChanged ? memberRepository.findByOrgRoleEntity_Id(id) : List.of();
    role = orgRoleRepository.save(role);
    auditService.log(
        AuditEventBuilder.roleUpdated(role, addedCaps, removedCaps, affectedMembers.size()));

    if (capsChanged) {
      // Evict cached member info for all affected members so they resolve updated capabilities
      String tenantId = RequestScopes.requireTenantId();
      for (var affectedMember : affectedMembers) {
        memberFilter.evictFromCache(tenantId, affectedMember.getClerkUserId());
      }

      try {
        for (var affectedMember : affectedMembers) {
          notificationService.createIfEnabled(
              affectedMember.getId(),
              "ROLE_PERMISSIONS_CHANGED",
              "Your permissions have been updated.",
              null,
              "ORG_ROLE",
              role.getId(),
              null);
        }
      } catch (Exception e) {
        log.warn(
            "Best-effort notification failed for role update (roleId={}): {}", id, e.getMessage());
      }
    }

    return OrgRoleResponse.from(role, memberRepository.countByOrgRoleEntity_Id(id));
  }

  @Transactional
  public MemberCapabilitiesResponse assignRole(UUID memberId, AssignRoleRequest request) {
    // locked: what the change grants is computed from the member's current role and overrides
    var member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    // Owner protection: cannot change the owner's role
    if (member.getOrgRoleEntity() != null && "owner".equals(member.getOrgRoleEntity().getSlug())) {
      throw new ForbiddenException(
          "Owner role protected", "Cannot change the role of the organization owner");
    }

    var role =
        orgRoleRepository
            .findById(request.orgRoleId())
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", request.orgRoleId()));

    requireSystemRoleGrantable(role);

    // Validate override format
    Set<String> overrides =
        request.capabilityOverrides() != null ? request.capabilityOverrides() : Set.of();
    for (String override : overrides) {
      if (override.length() < 2 || (!override.startsWith("+") && !override.startsWith("-"))) {
        throw new InvalidStateException(
            "Invalid override format",
            "Each override must start with '+' or '-' followed by a capability name: '"
                + override
                + "'");
      }
      String capName = override.substring(1);
      if (!Capability.ALL_NAMES.contains(capName)) {
        throw new InvalidStateException(
            "Invalid capability override",
            "Unknown capability: '" + capName + "'. Valid values: " + Capability.ALL_NAMES);
      }
    }

    // malformed for anyone: one override set adding and removing the same capability
    var contradictory =
        overrideCapabilities(overrides, "+").stream()
            .filter(overrideCapabilities(overrides, "-")::contains)
            .sorted()
            .toList();
    if (!contradictory.isEmpty()) {
      throw new InvalidStateException(
          "Contradictory capability override",
          "An override cannot both add and remove the same capability: "
              + String.join(", ", contradictory)
              + ".");
    }

    requireOwnerForNewOwnerOnlyCapabilities(member, role, overrides);

    // Resolve previous role name for audit
    String previousRole =
        member.getOrgRoleEntity() != null ? member.getOrgRoleEntity().getName() : "None";

    member.setOrgRoleEntity(role);
    member.setCapabilityOverrides(overrides);
    memberRepository.save(member);

    // Evict cached member info so subsequent requests resolve the new role
    String tenantId = RequestScopes.requireTenantId();
    memberFilter.evictFromCache(tenantId, member.getClerkUserId());

    auditService.log(
        AuditEventBuilder.memberRoleChanged(member, previousRole, role.getName(), overrides));
    notificationService.createIfEnabled(
        member.getId(),
        "ROLE_PERMISSIONS_CHANGED",
        "Your permissions have been updated.",
        null,
        "ORG_ROLE",
        role.getId(),
        null);

    return getMemberCapabilities(memberId);
  }

  @Transactional
  public void deleteRole(UUID id) {
    var role =
        orgRoleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", id));

    if (role.isSystem()) {
      throw new InvalidStateException("System role deletion", "System roles cannot be deleted");
    }

    long memberCount = memberRepository.countByOrgRoleEntity_Id(id);
    if (memberCount > 0) {
      throw new ResourceConflictException(
          "Role in use",
          "Cannot delete role '"
              + role.getName()
              + "' because it is assigned to "
              + memberCount
              + " member(s)");
    }

    // A pending invitation grants this exact role at first sign-in (by the id it stores), so the
    // role stays while one offers it — the invitee lands on the role the inviter chose, never a
    // silent fallback. (The invitation's FK would refuse the delete anyway, as a 500.)
    long pendingInvitations =
        invitationRepository.countByOrgRole_IdAndStatus(id, InvitationStatus.PENDING);
    if (pendingInvitations > 0) {
      throw new ResourceConflictException(
          "Role in use",
          "Cannot delete role '"
              + role.getName()
              + "' because it is offered in "
              + pendingInvitations
              + " pending invitation(s). Revoke the invitation(s) first.");
    }

    auditService.log(AuditEventBuilder.roleDeleted(role));
    orgRoleRepository.delete(role);
  }

  /**
   * Looks up a system role by its slug (e.g. "owner", "admin", "member"). Returns empty if not
   * found.
   */
  @Transactional(readOnly = true)
  public java.util.Optional<OrgRole> findSystemRoleBySlug(String slug) {
    return orgRoleRepository.findBySlug(slug).filter(OrgRole::isSystem);
  }

  /**
   * The role a pending invitation grants, by the id the invitation stores (system or custom). The
   * invitation's guards ran when it was created ({@code InvitationService.createInvitation}); the
   * role cannot be deleted while a pending invitation offers it ({@link #deleteRole}).
   */
  @Transactional(readOnly = true)
  public OrgRole findRoleForInvitation(UUID roleId) {
    return orgRoleRepository
        .findById(roleId)
        .orElseThrow(() -> new ResourceNotFoundException("OrgRole", roleId));
  }

  static String generateSlug(String name) {
    return name.toLowerCase()
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }

  /** Maps a Clerk role slug (e.g. "owner") to a human-readable display name. */
  private static String displayName(String clerkRole) {
    if (clerkRole == null) {
      return "None";
    }
    return switch (clerkRole) {
      case "owner" -> "Owner";
      case "admin" -> "Admin";
      case "member" -> "Member";
      default -> clerkRole;
    };
  }

  /**
   * The system-role rules for granting {@code role} to a member — by assignment or by invitation
   * (Task C): the Owner system role is never granted; the Admin system role only by an owner.
   */
  public static void requireSystemRoleGrantable(OrgRole role) {
    if (role.isSystem() && "owner".equals(role.getSlug())) {
      throw new ForbiddenException(
          "Owner role assignment forbidden", "Cannot assign the Owner system role to a member");
    }
    if (role.isSystem()
        && "admin".equals(role.getSlug())
        && !Roles.ORG_OWNER.equals(RequestScopes.getOrgRole())) {
      throw new ForbiddenException(
          "Admin role assignment restricted", "Only owners can assign the Admin system role");
    }
  }

  /**
   * FR-4 (founder ruling 2026-11-24): only an owner may put an owner-only capability ({@link
   * Capability#OWNER_ONLY}) into a role or into a member's effective capabilities. An admin holds
   * {@code TEAM_OVERSIGHT}, which opens role management and assignment, but must not reach the
   * owner-only capabilities through it. Refuses (403) when {@code capabilities} names one and the
   * caller is not the owner; {@code action} completes "Only the owner can …".
   */
  public static void requireOwnerToGrant(Collection<String> capabilities, String action) {
    var ownerOnly = capabilities.stream().filter(Capability.OWNER_ONLY::contains).sorted().toList();
    if (!ownerOnly.isEmpty() && !Roles.ORG_OWNER.equals(RequestScopes.getOrgRole())) {
      throw new ForbiddenException(
          "Owner-only capability",
          "Only the owner can " + action + ": " + String.join(", ", ownerOnly) + ".");
    }
  }

  /**
   * FR-4 on assignment: the founder ruling is about the member's EFFECTIVE capabilities, so a
   * non-owner is refused only when the new role and overrides give the member an owner-only
   * capability they do not hold now — assigning a role holding one ("assign a role …"), a {@code
   * +CAP}, or dropping a {@code -CAP} that kept one away ("grant …"). Editing the ordinary
   * overrides of a member already holding one, or taking one away, stays open.
   */
  private static void requireOwnerForNewOwnerOnlyCapabilities(
      Member member, OrgRole role, Set<String> overrides) {
    var before = effectiveCapabilities(member.getOrgRoleEntity(), member.getCapabilityOverrides());
    var gained =
        effectiveCapabilities(role, overrides).stream()
            .filter(Capability.OWNER_ONLY::contains)
            .filter(cap -> !before.contains(cap))
            .collect(Collectors.toSet());
    if (gained.isEmpty()) {
      return;
    }
    boolean roleChanges =
        member.getOrgRoleEntity() == null
            || !member.getOrgRoleEntity().getId().equals(role.getId());
    Set<String> roleCaps =
        role.getCapabilities().stream().map(Capability::name).collect(Collectors.toSet());
    if (roleChanges) {
      requireOwnerToGrant(
          gained.stream().filter(roleCaps::contains).toList(),
          "assign a role holding an owner-only capability");
      requireOwnerToGrant(
          gained.stream().filter(cap -> !roleCaps.contains(cap)).toList(),
          "grant an owner-only capability");
    } else {
      requireOwnerToGrant(gained, "grant an owner-only capability");
    }
  }

  private void validateCapabilities(Set<String> capabilities) {
    if (capabilities == null) {
      return;
    }
    for (String cap : capabilities) {
      try {
        Capability.fromString(cap);
      } catch (IllegalArgumentException e) {
        throw new InvalidStateException("Invalid capability", e.getMessage());
      }
    }
  }
}
