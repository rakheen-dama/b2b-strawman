package io.b2mash.b2b.b2bstrawman.deadline;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilingStatusRepository extends JpaRepository<FilingStatus, UUID> {

  Optional<FilingStatus> findByCustomerIdAndDeadlineTypeSlugAndPeriodKey(
      UUID customerId, String deadlineTypeSlug, String periodKey);

  @Query(
      "SELECT f FROM FilingStatus f WHERE f.customerId IN :customerIds"
          + " AND f.deadlineTypeSlug IN :slugs AND f.periodKey IN :periodKeys")
  List<FilingStatus> findByCustomerIdInAndDeadlineTypeSlugInAndPeriodKeyIn(
      @Param("customerIds") Collection<UUID> customerIds,
      @Param("slugs") Collection<String> slugs,
      @Param("periodKeys") Collection<String> periodKeys);

  List<FilingStatus> findByCustomerId(UUID customerId);

  List<FilingStatus> findByCustomerIdAndDeadlineTypeSlug(UUID customerId, String deadlineTypeSlug);

  List<FilingStatus> findByCustomerIdAndStatus(UUID customerId, String status);

  List<FilingStatus> findByCustomerIdAndDeadlineTypeSlugAndStatus(
      UUID customerId, String deadlineTypeSlug, String status);
}
