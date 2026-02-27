package io.b2mash.b2b.b2bstrawman.project;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
  @Query(
      """
      SELECT new io.b2mash.b2b.b2bstrawman.project.ProjectWithRole(
          p, pm.projectRole
      )
      FROM Project p JOIN ProjectMember pm ON p.id = pm.projectId
      WHERE pm.memberId = :memberId
      ORDER BY p.createdAt DESC
      """)
  List<ProjectWithRole> findProjectsForMember(@Param("memberId") UUID memberId);

  @Query(
      """
      SELECT new io.b2mash.b2b.b2bstrawman.project.ProjectWithRole(
          p, pm.projectRole
      )
      FROM Project p LEFT JOIN ProjectMember pm
        ON p.id = pm.projectId AND pm.memberId = :memberId
      ORDER BY p.createdAt DESC
      """)
  List<ProjectWithRole> findAllProjectsWithRole(@Param("memberId") UUID memberId);

  /** Lists all projects for an org-level user (admin/owner), optionally filtered by status. */
  @Query(
      """
      SELECT new io.b2mash.b2b.b2bstrawman.project.ProjectWithRole(
          p, pm.projectRole
      )
      FROM Project p LEFT JOIN ProjectMember pm
        ON p.id = pm.projectId AND pm.memberId = :memberId
      WHERE :status IS NULL OR p.status = :status
      ORDER BY p.createdAt DESC
      """)
  List<ProjectWithRole> findAllProjectsWithRoleAndStatus(
      @Param("memberId") UUID memberId, @Param("status") ProjectStatus status);

  /** Finds all projects linked to a specific customer. */
  @Query("SELECT p FROM Project p WHERE p.customerId = :customerId ORDER BY p.createdAt DESC")
  List<Project> findByCustomerId(@Param("customerId") UUID customerId);

  /** Counts projects linked to a specific customer. */
  @Query("SELECT COUNT(p) FROM Project p WHERE p.customerId = :customerId")
  long countByCustomerId(@Param("customerId") UUID customerId);

  /** Finds active projects with a due date before the given date (overdue). */
  @Query(
      """
      SELECT p FROM Project p
      WHERE p.status = :status
        AND p.dueDate < :dueDate
      ORDER BY p.dueDate ASC
      """)
  List<Project> findByStatusAndDueDateBefore(
      @Param("status") ProjectStatus status, @Param("dueDate") LocalDate dueDate);

  /** Counts projects with ACTIVE status in the current tenant schema. */
  @Query("SELECT COUNT(p) FROM Project p WHERE p.status = 'ACTIVE'")
  long countActiveProjects();

  /**
   * JPQL-based batch find by IDs. JPQL queries run against the current tenant schema (search_path
   * isolation), unlike JpaRepository.findAllById which uses EntityManager.find directly.
   */
}
