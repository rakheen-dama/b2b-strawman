package io.b2mash.b2b.b2bstrawman.keycloak.dto;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for creating a new organization via Keycloak auth mode. */
public record CreateOrgRequest(@NotBlank(message = "name is required") String name) {}
