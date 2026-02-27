package io.b2mash.b2b.b2bstrawman.clause;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Repository for {@link TemplateClause} entities. */
public interface TemplateClauseRepository extends JpaRepository<TemplateClause, UUID> {

  List<TemplateClause> findByTemplateIdOrderBySortOrderAsc(UUID templateId);

  List<TemplateClause> findByClauseId(UUID clauseId);

  @Modifying
  @Transactional
  void deleteByTemplateIdAndClauseId(UUID templateId, UUID clauseId);

  @Modifying
  @Transactional
  void deleteAllByTemplateId(UUID templateId);

  boolean existsByTemplateIdAndClauseId(UUID templateId, UUID clauseId);

  @Query(
      "SELECT COALESCE(MAX(tc.sortOrder), -1) FROM TemplateClause tc WHERE tc.templateId = :templateId")
  int findMaxSortOrderByTemplateId(@Param("templateId") UUID templateId);
}
