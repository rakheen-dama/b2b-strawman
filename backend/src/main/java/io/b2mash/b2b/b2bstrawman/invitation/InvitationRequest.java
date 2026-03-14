package io.b2mash.b2b.b2bstrawman.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InvitationRequest(@Email @NotNull String email, @NotNull UUID orgRoleId) {}
