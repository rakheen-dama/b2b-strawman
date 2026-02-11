package io.b2mash.b2b.b2bstrawman.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT c FROM Customer c WHERE c.id = :id")
  Optional<Customer> findOneById(@Param("id") UUID id);

  Optional<Customer> findByEmail(String email);

  boolean existsByEmail(String email);
}
