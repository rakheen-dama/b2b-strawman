package io.b2mash.b2b.b2bstrawman.invitation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PendingInvitationRepository extends JpaRepository<PendingInvitation, UUID> {

  Optional<PendingInvitation> findByOrgSlugAndEmailIgnoreCase(String orgSlug, String email);

  /**
   * Atomically deletes and returns the role for a pending invitation. Uses {@code DELETE ...
   * RETURNING} to avoid the read-then-delete race condition in multi-instance deployments.
   */
  @Modifying
  @Transactional
  @Query(
      value =
          "DELETE FROM public.pending_invitations"
              + " WHERE org_slug = :orgSlug AND lower(email) = lower(:email)"
              + " RETURNING role",
      nativeQuery = true)
  Optional<String> consumeByOrgSlugAndEmail(
      @Param("orgSlug") String orgSlug, @Param("email") String email);
}
