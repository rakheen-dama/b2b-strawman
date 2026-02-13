package io.b2mash.b2b.b2bstrawman.portal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, UUID> {

  Optional<MagicLinkToken> findByTokenHash(String tokenHash);

  long countByPortalContactIdAndCreatedAtAfter(UUID portalContactId, Instant after);

  List<MagicLinkToken> findByPortalContactIdOrderByCreatedAtDesc(UUID portalContactId);
}
