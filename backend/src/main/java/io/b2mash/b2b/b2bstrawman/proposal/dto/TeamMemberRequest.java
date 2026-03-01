package io.b2mash.b2b.b2bstrawman.proposal.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TeamMemberRequest(@NotNull UUID memberId, String role) {}
