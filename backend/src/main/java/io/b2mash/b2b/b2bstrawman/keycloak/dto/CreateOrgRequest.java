package io.b2mash.b2b.b2bstrawman.keycloak.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO for creating a new organization via Keycloak auth mode. */
public record CreateOrgRequest(
    @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name) {}
