package io.b2mash.b2b.b2bstrawman.proposal.dto;

import java.util.UUID;

public record TeamMemberRequest(UUID memberId, String role) {}
