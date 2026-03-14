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

  @Query(
      "SELECT m FROM Member m JOIN m.orgRoleEntity r WHERE r.slug IN :roleSlugs AND r.isSystem ="
          + " true")
  List<Member> findByOrgRoleIn(@Param("roleSlugs") List<String> roleSlugs);

  @Query("SELECT m FROM Member m JOIN FETCH m.orgRoleEntity")
  List<Member> findAllWithRole();

  long countByOrgRoleId(UUID orgRoleId);

  List<Member> findByOrgRoleId(UUID orgRoleId);
}
