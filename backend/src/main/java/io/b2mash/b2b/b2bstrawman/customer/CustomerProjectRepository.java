package io.b2mash.b2b.b2bstrawman.customer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerProjectRepository extends JpaRepository<CustomerProject, UUID> {

  List<CustomerProject> findByCustomerId(UUID customerId);

  List<CustomerProject> findByProjectId(UUID projectId);

  boolean existsByCustomerIdAndProjectId(UUID customerId, UUID projectId);

  @Modifying
  @Query(
      "DELETE FROM CustomerProject cp WHERE cp.customerId = :customerId AND cp.projectId = :projectId")
  void deleteByCustomerIdAndProjectId(
      @Param("customerId") UUID customerId, @Param("projectId") UUID projectId);
}
