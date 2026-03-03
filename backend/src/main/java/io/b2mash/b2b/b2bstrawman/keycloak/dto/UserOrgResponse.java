package io.b2mash.b2b.b2bstrawman.keycloak.dto;

/** Response DTO for listing a user's organizations. */
public record UserOrgResponse(String id, String name, String slug, String role) {}
