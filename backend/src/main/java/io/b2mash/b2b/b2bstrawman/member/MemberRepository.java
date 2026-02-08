package io.b2mash.b2b.b2bstrawman.member;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  Optional<Member> findByClerkUserId(String clerkUserId);

  void deleteByClerkUserId(String clerkUserId);

  boolean existsByClerkUserId(String clerkUserId);
}
