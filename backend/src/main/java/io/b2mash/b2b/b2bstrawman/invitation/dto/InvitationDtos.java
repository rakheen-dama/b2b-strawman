package io.b2mash.b2b.b2bstrawman.invitation.dto;

import io.b2mash.b2b.b2bstrawman.invitation.PendingInvitation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvitationDtos {

  private InvitationDtos() {}

  public record CreateInvitationRequest(@NotBlank @Email String email, @NotNull UUID orgRoleId) {}

  public record PendingInvitationResponse(
      UUID id,
      String email,
      String roleName,
      String roleSlug,
      String invitedByName,
      String status,
      Instant expiresAt,
      Instant createdAt,
      Instant acceptedAt) {

    public static PendingInvitationResponse from(PendingInvitation invitation) {
      return new PendingInvitationResponse(
          invitation.getId(),
          invitation.getEmail(),
          invitation.getOrgRole().getName(),
          invitation.getOrgRole().getSlug(),
          invitation.getInvitedBy().getName(),
          invitation.getStatus().name(),
          invitation.getExpiresAt(),
          invitation.getCreatedAt(),
          invitation.getAcceptedAt());
    }
  }

  public record InvitationListResponse(List<PendingInvitationResponse> invitations) {}
}
