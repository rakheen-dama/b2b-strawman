package io.b2mash.b2b.b2bstrawman.reporting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {
  Optional<ReportDefinition> findBySlug(String slug);

  List<ReportDefinition> findAllByOrderByCategoryAscSortOrderAsc();
}
