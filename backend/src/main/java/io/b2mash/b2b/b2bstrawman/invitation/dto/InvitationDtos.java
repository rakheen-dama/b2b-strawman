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
      Instant acceptedAt,
      String emailNotSentReason) {

    /**
     * Fix 6 (Keycloak-stack OBS F-2), set on the create response only: Keycloak sent no invitation
     * email because the person already has a login in the firm's Keycloak organization. Null when
     * an email went out, in mock mode, when the best-effort Keycloak call failed, and in list
     * responses.
     */
    public static final String ALREADY_HAS_LOGIN = "ALREADY_HAS_LOGIN";

    public static PendingInvitationResponse from(PendingInvitation invitation) {
      return from(invitation, null);
    }

    public static PendingInvitationResponse from(
        PendingInvitation invitation, String emailNotSentReason) {
      return new PendingInvitationResponse(
          invitation.getId(),
          invitation.getEmail(),
          invitation.getOrgRole().getName(),
          invitation.getOrgRole().getSlug(),
          invitation.getInvitedBy().getName(),
          invitation.getStatus().name(),
          invitation.getExpiresAt(),
          invitation.getCreatedAt(),
          invitation.getAcceptedAt(),
          emailNotSentReason);
    }
  }

  public record InvitationListResponse(List<PendingInvitationResponse> invitations) {}
}
