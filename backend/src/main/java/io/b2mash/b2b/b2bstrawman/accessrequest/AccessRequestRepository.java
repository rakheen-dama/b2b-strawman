package io.b2mash.b2b.b2bstrawman.accessrequest;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

  Optional<AccessRequest> findByEmailAndStatus(String email, AccessRequestStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AccessRequest> findWithLockByEmailAndStatus(String email, AccessRequestStatus status);

  boolean existsByEmailAndStatusIn(String email, List<AccessRequestStatus> statuses);

  List<AccessRequest> findByStatusOrderByCreatedAtAsc(AccessRequestStatus status);

  @Modifying
  @Transactional
  void deleteByStatusAndOtpExpiresAtBefore(AccessRequestStatus status, Instant cutoff);
}
