package io.b2mash.b2b.b2bstrawman.invitation;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
    UUID id,
    String email,
    String roleName,
    String status,
    Instant expiresAt,
    Instant createdAt,
    String invitedByName) {

  public static InvitationResponse from(
      PendingInvitation inv, String roleName, String inviterName) {
    return new InvitationResponse(
        inv.getId(),
        inv.getEmail(),
        roleName,
        inv.getStatus(),
        inv.getExpiresAt(),
        inv.getCreatedAt(),
        inviterName);
  }
}
