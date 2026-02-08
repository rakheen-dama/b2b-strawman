package io.b2mash.b2b.b2bstrawman.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

  List<ProjectMember> findByProjectId(UUID projectId);

  Optional<ProjectMember> findByProjectIdAndMemberId(UUID projectId, UUID memberId);

  boolean existsByProjectIdAndMemberId(UUID projectId, UUID memberId);

  List<ProjectMember> findByProjectIdAndProjectRole(UUID projectId, String projectRole);

  List<ProjectMember> findByMemberId(UUID memberId);

  void deleteByProjectIdAndMemberId(UUID projectId, UUID memberId);

  @Query(
      """
      SELECT new io.b2mash.b2b.b2bstrawman.member.ProjectMemberInfo(
          pm.id, pm.memberId, m.name, m.email, m.avatarUrl, pm.projectRole, pm.createdAt
      )
      FROM ProjectMember pm JOIN Member m ON pm.memberId = m.id
      WHERE pm.projectId = :projectId
      ORDER BY pm.createdAt ASC
      """)
  List<ProjectMemberInfo> findProjectMembersWithDetails(@Param("projectId") UUID projectId);
}
