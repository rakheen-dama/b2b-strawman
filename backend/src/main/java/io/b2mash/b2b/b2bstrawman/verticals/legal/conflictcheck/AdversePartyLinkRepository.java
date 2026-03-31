package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdversePartyLinkRepository extends JpaRepository<AdversePartyLink, UUID> {

  List<AdversePartyLink> findByProjectId(UUID projectId);

  List<AdversePartyLink> findByAdversePartyId(UUID adversePartyId);

  long countByAdversePartyId(UUID adversePartyId);

  boolean existsByAdversePartyId(UUID adversePartyId);

  Optional<AdversePartyLink> findByAdversePartyIdAndProjectId(UUID adversePartyId, UUID projectId);

  @Query(
      """
      SELECT l.adversePartyId, COUNT(l) FROM AdversePartyLink l
      WHERE l.adversePartyId IN :adversePartyIds
      GROUP BY l.adversePartyId
      """)
  List<Object[]> countByAdversePartyIdIn(
      @Param("adversePartyIds") Collection<UUID> adversePartyIds);
}
