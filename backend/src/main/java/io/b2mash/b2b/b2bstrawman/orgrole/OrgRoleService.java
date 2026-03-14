package io.b2mash.b2b.b2bstrawman.orgrole;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
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

  public OrgRoleService(
      OrgRoleRepository orgRoleRepository,
      MemberRepository memberRepository,
      AuditService auditService,
      NotificationService notificationService,
      @Lazy MemberFilter memberFilter) {
    this.orgRoleRepository = orgRoleRepository;
    this.memberRepository = memberRepository;
    this.auditService = auditService;
    this.notificationService = notificationService;
    this.memberFilter = memberFilter;
  }

  @Transactional(readOnly = true)
  public Set<String> resolveCapabilities(UUID memberId) {
    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    if (member.getOrgRoleId() == null) {
      // Fallback: if no explicit OrgRole is assigned, derive capabilities from the legacy orgRole
      // string. This ensures owner/admin members who predate the OrgRole system still get full
      // capabilities resolved.
      String legacyRole = member.getOrgRole();
      if ("owner".equals(legacyRole) || "admin".equals(legacyRole)) {
        return Set.copyOf(Capability.ALL_NAMES);
      }
      return Collections.emptySet();
    }

    var role =
        orgRoleRepository
            .findById(member.getOrgRoleId())
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", member.getOrgRoleId()));

    if (role.isSystem() && ("owner".equals(role.getSlug()) || "admin".equals(role.getSlug()))) {
      return Set.copyOf(Capability.ALL_NAMES);
    }

    Set<String> effective =
        role.getCapabilities().stream().map(Capability::name).collect(Collectors.toSet());
    effective = new HashSet<>(effective);

    for (String override : member.getCapabilityOverrides()) {
      String capName = override.substring(1);
      if (!Capability.ALL_NAMES.contains(capName)) {
        continue; // skip invalid overrides for forward-compatibility
      }
      if (override.startsWith("+")) {
        effective.add(capName);
      } else if (override.startsWith("-")) {
        effective.remove(capName);
      }
    }

    return effective;
  }

  @Transactional(readOnly = true)
  public MyCapabilitiesResponse getMyCapabilities(UUID memberId) {
    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    String roleName = null;
    if (member.getOrgRoleId() != null) {
      roleName =
          orgRoleRepository.findById(member.getOrgRoleId()).map(OrgRole::getName).orElse(null);
    }

    Set<String> capabilities = RequestScopes.getCapabilities();
    String orgRole = RequestScopes.getOrgRole();
    boolean isAdmin = "admin".equals(orgRole);
    boolean isOwner = "owner".equals(orgRole);

    return new MyCapabilitiesResponse(capabilities, roleName, isAdmin, isOwner);
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

    if (member.getOrgRoleId() != null) {
      var role = orgRoleRepository.findById(member.getOrgRoleId()).orElse(null);
      if (role != null) {
        roleName = role.getName();
        roleCapabilities =
            role.getCapabilities().stream().map(Capability::name).collect(Collectors.toSet());
      }
    }

    Set<String> effectiveCapabilities = resolveCapabilities(memberId);

    return new MemberCapabilitiesResponse(
        memberId,
        roleName,
        roleCapabilities,
        member.getCapabilityOverrides(),
        effectiveCapabilities);
  }

  @Transactional(readOnly = true)
  public List<OrgRoleResponse> listRoles() {
    return orgRoleRepository.findAll().stream()
        .map(role -> OrgRoleResponse.from(role, memberRepository.countByOrgRoleId(role.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public OrgRoleResponse getRole(UUID id) {
    var role =
        orgRoleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", id));
    return OrgRoleResponse.from(role, memberRepository.countByOrgRoleId(id));
  }

  @Transactional
  public OrgRoleResponse createRole(CreateOrgRoleRequest request) {
    if (orgRoleRepository.existsByNameIgnoreCase(request.name())) {
      throw new ResourceConflictException(
          "Role name conflict", "A role with the name '" + request.name() + "' already exists");
    }

    validateCapabilities(request.capabilities());

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
    var role =
        orgRoleRepository
            .findById(id)
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
      role.setCapabilities(
          request.capabilities().stream().map(Capability::valueOf).collect(Collectors.toSet()));
    }

    List<Member> affectedMembers = capsChanged ? memberRepository.findByOrgRoleId(id) : List.of();
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

    return OrgRoleResponse.from(role, memberRepository.countByOrgRoleId(id));
  }

  @Transactional
  public MemberCapabilitiesResponse assignRole(UUID memberId, AssignRoleRequest request) {
    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    // Owner protection: cannot change the owner's role
    if ("owner".equals(member.getOrgRole())) {
      throw new ForbiddenException(
          "Owner role protected", "Cannot change the role of the organization owner");
    }

    var role =
        orgRoleRepository
            .findById(request.orgRoleId())
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", request.orgRoleId()));

    // Cannot assign the OWNER system role to anyone
    if (role.isSystem() && "owner".equals(role.getSlug())) {
      throw new ForbiddenException(
          "Owner role assignment forbidden", "Cannot assign the Owner system role to a member");
    }

    // ADMIN system role can only be assigned by owners
    if (role.isSystem() && "admin".equals(role.getSlug())) {
      String callerRole = RequestScopes.getOrgRole();
      if (!"owner".equals(callerRole)) {
        throw new ForbiddenException(
            "Admin role assignment restricted", "Only owners can assign the Admin system role");
      }
    }

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

    // Resolve previous role name for audit
    String previousRole;
    if (member.getOrgRoleId() != null) {
      previousRole =
          orgRoleRepository
              .findById(member.getOrgRoleId())
              .map(OrgRole::getName)
              .orElse(displayName(member.getOrgRole()));
    } else {
      previousRole = displayName(member.getOrgRole());
    }

    member.setOrgRoleId(request.orgRoleId());
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

    long memberCount = memberRepository.countByOrgRoleId(id);
    if (memberCount > 0) {
      throw new ResourceConflictException(
          "Role in use",
          "Cannot delete role '"
              + role.getName()
              + "' because it is assigned to "
              + memberCount
              + " member(s)");
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
