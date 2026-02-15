package io.b2mash.b2b.b2bstrawman.tag;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntityTagRepository extends JpaRepository<EntityTag, UUID> {

  @Query(
      "SELECT et FROM EntityTag et WHERE et.entityType = :entityType AND et.entityId = :entityId")
  List<EntityTag> findByEntityTypeAndEntityId(
      @Param("entityType") String entityType, @Param("entityId") UUID entityId);

  @Modifying
  @Query("DELETE FROM EntityTag et WHERE et.entityType = :entityType AND et.entityId = :entityId")
  void deleteByEntityTypeAndEntityId(
      @Param("entityType") String entityType, @Param("entityId") UUID entityId);

  @Query("SELECT COUNT(et) FROM EntityTag et WHERE et.tagId = :tagId")
  long countByTagId(@Param("tagId") UUID tagId);
}
