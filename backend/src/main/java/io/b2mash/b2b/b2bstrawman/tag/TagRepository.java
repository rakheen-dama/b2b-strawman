package io.b2mash.b2b.b2bstrawman.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT t FROM Tag t WHERE t.id = :id")
  Optional<Tag> findOneById(@Param("id") UUID id);

  @Query("SELECT t FROM Tag t ORDER BY t.name ASC")
  List<Tag> findByOrderByNameAsc();

  @Query("SELECT t FROM Tag t WHERE LOWER(t.name) LIKE LOWER(CONCAT(:prefix, '%')) ORDER BY t.name")
  List<Tag> findByNameStartingWithIgnoreCaseOrderByName(@Param("prefix") String prefix);

  @Query("SELECT t FROM Tag t WHERE t.slug = :slug")
  Optional<Tag> findBySlug(@Param("slug") String slug);

  /**
   * JPQL-based findAllByIds that respects Hibernate @Filter (unlike JpaRepository.findAllById which
   * uses EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT t FROM Tag t WHERE t.id IN :ids")
  List<Tag> findAllByIds(@Param("ids") List<UUID> ids);
}
