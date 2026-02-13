package io.b2mash.b2b.b2bstrawman.portal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, UUID> {

  Optional<MagicLinkToken> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM MagicLinkToken t WHERE t.tokenHash = :tokenHash")
  Optional<MagicLinkToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  long countByPortalContactIdAndCreatedAtAfter(UUID portalContactId, Instant after);

  int deleteByExpiresAtBefore(Instant before);
}
