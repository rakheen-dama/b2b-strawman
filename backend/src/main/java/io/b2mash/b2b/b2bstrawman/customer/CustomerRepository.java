package io.b2mash.b2b.b2bstrawman.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByEmail(String email);

  boolean existsByEmail(String email);
}
