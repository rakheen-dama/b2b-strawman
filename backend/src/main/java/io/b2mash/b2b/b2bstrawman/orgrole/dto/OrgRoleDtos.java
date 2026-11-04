package io.b2mash.b2b.b2bstrawman.orgrole.dto;

import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class OrgRoleDtos {

  private OrgRoleDtos() {}

  public record CreateOrgRoleRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 500) String description,
      @NotNull Set<String> capabilities) {}

  public record UpdateOrgRoleRequest(
      @Size(max = 100) String name,
      @Size(max = 500) String description,
      Set<String> capabilities) {}

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

  public record AssignRoleRequest(@NotNull UUID orgRoleId, Set<String> capabilityOverrides) {}

  public record MemberCapabilitiesResponse(
      UUID memberId,
      String roleName,
      Set<String> roleCapabilities,
      Set<String> overrides,
      Set<String> effectiveCapabilities) {}

  /**
   * The caller's effective capabilities. {@code memberId} is the caller's {@code members.id} — the
   * id the backend compares for self-approval (630B, ADR-362), so the UI can hide it.
   */
  public record MyCapabilitiesResponse(
      Set<String> capabilities, String role, boolean isAdmin, boolean isOwner, UUID memberId) {}
}
