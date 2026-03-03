package io.b2mash.b2b.b2bstrawman.keycloak.dto;

public record KeycloakOrgMember(
    String id, String username, String email, String firstName, String lastName) {}
