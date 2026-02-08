package io.b2mash.b2b.b2bstrawman.project;

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
}
