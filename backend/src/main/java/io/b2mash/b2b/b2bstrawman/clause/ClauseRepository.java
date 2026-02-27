package io.b2mash.b2b.b2bstrawman.clause;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository for {@link Clause} entities. */
public interface ClauseRepository extends JpaRepository<Clause, UUID> {

  List<Clause> findByActiveTrueOrderByCategoryAscSortOrderAsc();

  Optional<Clause> findBySlug(String slug);

  List<Clause> findByCategoryAndActiveTrueOrderBySortOrderAsc(String category);

  List<Clause> findByPackIdAndSourceAndActiveTrue(String packId, ClauseSource source);

  List<Clause> findAllByOrderByCategoryAscSortOrderAsc();

  @Query("SELECT DISTINCT c.category FROM Clause c WHERE c.active = true ORDER BY c.category")
  List<String> findDistinctActiveCategories();
}
