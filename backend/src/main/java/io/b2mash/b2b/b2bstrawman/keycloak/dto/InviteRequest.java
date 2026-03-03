package io.b2mash.b2b.b2bstrawman.keycloak.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request DTO for inviting a user to an organization. */
public record InviteRequest(
    @NotBlank(message = "email is required") @Email(message = "email must be a valid email address")
        String email,
    String role) {}
