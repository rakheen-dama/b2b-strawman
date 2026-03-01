package io.b2mash.b2b.b2bstrawman.proposal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProposalTeamMemberRepository extends JpaRepository<ProposalTeamMember, UUID> {

  List<ProposalTeamMember> findByProposalIdOrderBySortOrder(UUID proposalId);

  @Modifying
  @Transactional
  @Query("DELETE FROM ProposalTeamMember m WHERE m.proposalId = :proposalId")
  void deleteByProposalId(@Param("proposalId") UUID proposalId);
}
