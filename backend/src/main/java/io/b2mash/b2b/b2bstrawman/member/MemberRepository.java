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

  List<Member> findByEmailEndingWith(String suffix);

  @Query("SELECT m FROM Member m WHERE m.orgRoleEntity.slug IN :slugs")
  List<Member> findByRoleSlugsIn(@Param("slugs") List<String> slugs);

  long countByOrgRoleEntity_Id(UUID orgRoleId);

  List<Member> findByOrgRoleEntity_Id(UUID orgRoleId);

  boolean existsByEmail(String email);
}
