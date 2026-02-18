package io.b2mash.b2b.b2bstrawman.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, UUID> {

  @Query("SELECT t FROM Tag t ORDER BY t.name ASC")
  List<Tag> findByOrderByNameAsc();

  @Query("SELECT t FROM Tag t WHERE LOWER(t.name) LIKE LOWER(CONCAT(:prefix, '%')) ORDER BY t.name")
  List<Tag> findByNameStartingWithIgnoreCaseOrderByName(@Param("prefix") String prefix);

  @Query("SELECT t FROM Tag t WHERE t.slug = :slug")
  Optional<Tag> findBySlug(@Param("slug") String slug);
}
