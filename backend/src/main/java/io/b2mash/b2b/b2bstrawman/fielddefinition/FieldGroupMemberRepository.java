package io.b2mash.b2b.b2bstrawman.fielddefinition;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldGroupMemberRepository extends JpaRepository<FieldGroupMember, UUID> {

  @Query(
      "SELECT fgm FROM FieldGroupMember fgm WHERE fgm.fieldGroupId = :fieldGroupId"
          + " ORDER BY fgm.sortOrder")
  List<FieldGroupMember> findByFieldGroupIdOrderBySortOrder(
      @Param("fieldGroupId") UUID fieldGroupId);

  @Modifying
  @Query(
      "DELETE FROM FieldGroupMember fgm WHERE fgm.fieldGroupId = :fieldGroupId"
          + " AND fgm.fieldDefinitionId = :fieldDefinitionId")
  void deleteByFieldGroupIdAndFieldDefinitionId(
      @Param("fieldGroupId") UUID fieldGroupId, @Param("fieldDefinitionId") UUID fieldDefinitionId);
}
