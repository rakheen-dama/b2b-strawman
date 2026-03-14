package io.b2mash.b2b.b2bstrawman.invitation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PendingInvitationRepository extends JpaRepository<PendingInvitation, UUID> {

  Optional<PendingInvitation> findByOrgSlugAndEmailIgnoreCase(String orgSlug, String email);

  @Transactional
  void deleteByOrgSlugAndEmailIgnoreCase(String orgSlug, String email);
}
