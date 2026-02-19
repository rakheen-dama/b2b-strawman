package io.b2mash.b2b.b2bstrawman.customer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByEmail(String email);

  boolean existsByEmail(String email);

  List<Customer> findByLifecycleStatus(LifecycleStatus lifecycleStatus);

  @Query(
      "SELECT c.id FROM Customer c WHERE c.lifecycleStatus = :status AND c.offboardedAt < :before")
  List<UUID> findIdsByLifecycleStatusAndOffboardedAtBefore(
      @Param("status") LifecycleStatus status, @Param("before") Instant before);

  /**
   * Counts customers grouped by lifecycle status. Only ACTIVE customers are included (archived are
   * excluded).
   */
  @Query(
      "SELECT c.lifecycleStatus AS lifecycleStatus, COUNT(c) AS cnt FROM Customer c"
          + " WHERE c.status = 'ACTIVE'"
          + " GROUP BY c.lifecycleStatus")
  List<LifecycleStatusCount> countByLifecycleStatus();

  interface LifecycleStatusCount {
    LifecycleStatus getLifecycleStatus();

    long getCnt();
  }
}
