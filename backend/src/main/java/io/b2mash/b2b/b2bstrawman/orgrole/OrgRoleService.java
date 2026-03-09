package io.b2mash.b2b.b2bstrawman.orgrole;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.OrgRoleResponse;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.UpdateOrgRoleRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OrgRoleService {

  private final OrgRoleRepository orgRoleRepository;
  private final MemberRepository memberRepository;

  public OrgRoleService(OrgRoleRepository orgRoleRepository, MemberRepository memberRepository) {
    this.orgRoleRepository = orgRoleRepository;
    this.memberRepository = memberRepository;
  }

  public Set<String> resolveCapabilities(UUID memberId) {
    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    if (member.getOrgRoleId() == null) {
      throw new ResourceNotFoundException(
          "OrgRole", "null (member " + memberId + " has no role assigned)");
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
      if (override.startsWith("+")) {
        effective.add(override.substring(1));
      } else if (override.startsWith("-")) {
        effective.remove(override.substring(1));
      }
    }

    return effective;
  }

  public List<OrgRoleResponse> listRoles() {
    return orgRoleRepository.findAll().stream()
        .map(role -> OrgRoleResponse.from(role, memberRepository.countByOrgRoleId(role.getId())))
        .toList();
  }

  public OrgRoleResponse getRole(UUID id) {
    var role =
        orgRoleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OrgRole", id));
    return OrgRoleResponse.from(role, memberRepository.countByOrgRoleId(id));
  }

  public OrgRoleResponse createRole(CreateOrgRoleRequest request) {
    if (orgRoleRepository.existsByNameIgnoreCase(request.name())) {
      throw new ResourceConflictException(
          "Role name conflict", "A role with the name '" + request.name() + "' already exists");
    }

    if (request.capabilities() != null) {
      for (String cap : request.capabilities()) {
        Capability.fromString(cap);
      }
    }

    String slug = generateSlug(request.name());
    var role = new OrgRole(request.name(), slug, request.description(), false);

    if (request.capabilities() != null) {
      role.setCapabilities(
          request.capabilities().stream().map(Capability::valueOf).collect(Collectors.toSet()));
    }

    role = orgRoleRepository.save(role);
    return OrgRoleResponse.from(role, 0);
  }

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
      String newSlug = generateSlug(request.name());
      if (orgRoleRepository.existsBySlugAndIdNot(newSlug, id)) {
        throw new ResourceConflictException(
            "Role name conflict", "A role with the name '" + request.name() + "' already exists");
      }
      role.setName(request.name());
      role.setSlug(newSlug);
    }

    if (request.description() != null) {
      role.setDescription(request.description());
    }

    if (request.capabilities() != null) {
      for (String cap : request.capabilities()) {
        Capability.fromString(cap);
      }
      role.setCapabilities(
          request.capabilities().stream().map(Capability::valueOf).collect(Collectors.toSet()));
    }

    role = orgRoleRepository.save(role);
    return OrgRoleResponse.from(role, memberRepository.countByOrgRoleId(id));
  }

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

    orgRoleRepository.delete(role);
  }

  static String generateSlug(String name) {
    return name.toLowerCase()
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }
}
