package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdversePartyLinkRepository extends JpaRepository<AdversePartyLink, UUID> {

  List<AdversePartyLink> findByProjectId(UUID projectId);

  List<AdversePartyLink> findByAdversePartyId(UUID adversePartyId);

  boolean existsByAdversePartyId(UUID adversePartyId);

  Optional<AdversePartyLink> findByAdversePartyIdAndProjectId(UUID adversePartyId, UUID projectId);
}
