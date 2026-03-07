package io.b2mash.b2b.b2bstrawman.accessrequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

  Optional<AccessRequest> findByEmailAndStatus(String email, AccessRequestStatus status);

  boolean existsByEmailAndStatusIn(String email, List<AccessRequestStatus> statuses);

  List<AccessRequest> findByStatusOrderByCreatedAtAsc(AccessRequestStatus status);
}
