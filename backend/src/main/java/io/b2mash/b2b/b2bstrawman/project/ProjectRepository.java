package io.b2mash.b2b.b2bstrawman.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT p FROM Project p WHERE p.id = :id")
  Optional<Project> findOneById(@Param("id") UUID id);

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

  /** Counts active (non-archived) projects in the current tenant. Hibernate @Filter applies. */
  @Query("SELECT COUNT(p) FROM Project p")
  long countActiveProjects();
}
