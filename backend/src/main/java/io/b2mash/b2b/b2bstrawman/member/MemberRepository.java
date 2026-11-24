package io.b2mash.b2b.b2bstrawman.member;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  /**
   * The member row {@code FOR UPDATE}: a role assignment decides what it grants from the member's
   * current role and overrides, so concurrent assignments are serialised (FR-4).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.id = :id")
  Optional<Member> findByIdForUpdate(@Param("id") UUID id);
}
