package io.b2mash.b2b.b2bstrawman.budget;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectBudgetRepository extends JpaRepository<ProjectBudget, UUID> {
  /**
   * Finds the budget for a project. JPQL query scoped to the current tenant schema via search_path.
   */
  @Query("SELECT pb FROM ProjectBudget pb WHERE pb.projectId = :projectId")
  Optional<ProjectBudget> findByProjectId(@Param("projectId") UUID projectId);
}
