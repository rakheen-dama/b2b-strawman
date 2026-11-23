package io.b2mash.b2b.b2bstrawman.customer;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  /** JPQL-based batch find by IDs — respects tenant schema isolation (unlike findAllById). */
  @Query("SELECT c FROM Customer c WHERE c.id IN :ids")
  List<Customer> findByIdIn(@Param("ids") Collection<UUID> ids);

  Optional<Customer> findByEmail(String email);

  boolean existsByEmail(String email);

  List<Customer> findByLifecycleStatus(LifecycleStatus lifecycleStatus);

  /** {@code status} is the archive flag ("ACTIVE"/"ARCHIVED"), independent of lifecycle. */
  List<Customer> findByStatusAndLifecycleStatusIn(
      String status, Collection<LifecycleStatus> lifecycleStatuses);

  @Query(
      "SELECT c.id FROM Customer c WHERE c.lifecycleStatus = :status AND c.offboardedAt < :before")
  List<UUID> findIdsByLifecycleStatusAndOffboardedAtBefore(
      @Param("status") LifecycleStatus status, @Param("before") Instant before);

  @Query(
      value =
          "SELECT lifecycle_status, COUNT(*) AS cnt FROM customers WHERE status = 'ACTIVE' GROUP BY lifecycle_status",
      nativeQuery = true)
  List<Object[]> countByLifecycleStatus();

  @Query(
      value =
          "SELECT * FROM customers"
              + " WHERE public.similarity(lower(name), lower(:name)) > :threshold"
              + " ORDER BY public.similarity(lower(name), lower(:name)) DESC"
              + " LIMIT :maxResults",
      nativeQuery = true)
  List<Customer> findBySimilarName(
      @Param("name") String name,
      @Param("threshold") double threshold,
      @Param("maxResults") int maxResults);

  @Query(value = "SELECT * FROM customers WHERE id_number = :idNumber", nativeQuery = true)
  Optional<Customer> findByIdNumberExact(@Param("idNumber") String idNumber);

  /**
   * Returns all customers with the given registration number. Returns a {@link List} rather than
   * {@link Optional} because import / migration data routinely produces duplicate registration
   * numbers — the legal conflict-check flow needs to iterate every match rather than fail with
   * {@code IncorrectResultSizeDataAccessException}.
   */
  @Query(
      value = "SELECT * FROM customers WHERE registration_number = :registrationNumber",
      nativeQuery = true)
  List<Customer> findByRegistrationNumber(@Param("registrationNumber") String registrationNumber);

  // --- Take-on dedup (Phase 93 655A, §17.3.3) ---

  /** The client holding {@code email}, case-insensitively (take-on: email clash at commit). */
  Optional<Customer> findFirstByEmailIgnoreCase(String email);

  /** Native projection of the fields the take-on dedup compares. */
  interface DedupCandidate {
    UUID getId();

    String getEmail();

    String getName();

    String getTaxNumber();

    String getIdNumber();
  }

  /**
   * Every client the take-on dry run could match (anonymised clients excluded): loaded once per
   * validation in its snapshot (X2).
   */
  @Query(
      nativeQuery = true,
      value =
          "SELECT id, email, name, tax_number AS \"taxNumber\", id_number AS \"idNumber\""
              + " FROM customers"
              + " WHERE lifecycle_status <> 'ANONYMIZED'")
  List<DedupCandidate> findTakeOnDedupCandidates();

  /** A client with this name (case-insensitive) and tax number — the take-on commit re-check. */
  @Query(
      nativeQuery = true,
      value =
          "SELECT id FROM customers WHERE lower(name) = lower(:name) AND tax_number = :taxNumber"
              + " AND lifecycle_status <> 'ANONYMIZED' ORDER BY created_at LIMIT 1")
  Optional<UUID> findIdByNameAndTaxNumber(
      @Param("name") String name, @Param("taxNumber") String taxNumber);

  /** A client with this name (case-insensitive) and ID number — the take-on commit re-check. */
  @Query(
      nativeQuery = true,
      value =
          "SELECT id FROM customers WHERE lower(name) = lower(:name) AND id_number = :idNumber"
              + " AND lifecycle_status <> 'ANONYMIZED' ORDER BY created_at LIMIT 1")
  Optional<UUID> findIdByNameAndIdNumber(
      @Param("name") String name, @Param("idNumber") String idNumber);
}
