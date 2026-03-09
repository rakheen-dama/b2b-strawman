package io.b2mash.b2b.b2bstrawman.orgrole.dto;

import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class OrgRoleDtos {

  private OrgRoleDtos() {}

  public record CreateOrgRoleRequest(String name, String description, Set<String> capabilities) {}

  public record UpdateOrgRoleRequest(String name, String description, Set<String> capabilities) {}

  public record OrgRoleResponse(
      UUID id,
      String name,
      String slug,
      String description,
      Set<String> capabilities,
      boolean isSystem,
      long memberCount,
      Instant createdAt,
      Instant updatedAt) {

    public static OrgRoleResponse from(OrgRole role, long memberCount) {
      return new OrgRoleResponse(
          role.getId(),
          role.getName(),
          role.getSlug(),
          role.getDescription(),
          role.getCapabilities().stream().map(Capability::name).collect(Collectors.toSet()),
          role.isSystem(),
          memberCount,
          role.getCreatedAt(),
          role.getUpdatedAt());
    }
  }

  public record AssignRoleRequest(UUID orgRoleId, Set<String> capabilityOverrides) {}

  public record MemberCapabilitiesResponse(
      UUID memberId,
      String roleName,
      Set<String> roleCapabilities,
      Set<String> overrides,
      Set<String> effectiveCapabilities) {}

  public record MyCapabilitiesResponse(
      Set<String> capabilities, String role, boolean isAdmin, boolean isOwner) {}
}
