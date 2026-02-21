package io.b2mash.b2b.b2bstrawman.member;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberInfo(
    UUID id,
    UUID memberId,
    String name,
    String email,
    String avatarUrl,
    String projectRole,
    String orgRole,
    Instant createdAt) {}
