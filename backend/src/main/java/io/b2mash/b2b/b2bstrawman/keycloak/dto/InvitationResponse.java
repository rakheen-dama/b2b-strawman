package io.b2mash.b2b.b2bstrawman.keycloak.dto;

/** Response DTO for listing pending invitations. */
public record InvitationResponse(String id, String email, String status, String createdAt) {}
