package io.b2mash.b2b.b2bstrawman.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {
  Optional<Member> findByClerkUserId(String clerkUserId);

  void deleteByClerkUserId(String clerkUserId);

  boolean existsByClerkUserId(String clerkUserId);

  @Query("SELECT m FROM Member m WHERE m.orgRole IN :roles")
  List<Member> findByOrgRoleIn(@Param("roles") List<String> roles);
}
