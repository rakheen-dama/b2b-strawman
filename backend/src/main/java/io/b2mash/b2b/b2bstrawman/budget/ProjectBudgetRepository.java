package io.b2mash.b2b.b2bstrawman.budget;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectBudgetRepository extends JpaRepository<ProjectBudget, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT pb FROM ProjectBudget pb WHERE pb.id = :id")
  Optional<ProjectBudget> findOneById(@Param("id") UUID id);

  /** Finds the budget for a project. JPQL query respects Hibernate @Filter for tenant isolation. */
  @Query("SELECT pb FROM ProjectBudget pb WHERE pb.projectId = :projectId")
  Optional<ProjectBudget> findByProjectId(@Param("projectId") UUID projectId);
}
