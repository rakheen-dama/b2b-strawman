package io.b2mash.b2b.b2bstrawman.customer;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  /**
   * A client with this name and tax number — the take-on commit re-check. Both compared trimmed and
   * case-insensitively, exactly as the dry run's name key does.
   */
  @Query(
      nativeQuery = true,
      value =
          "SELECT id FROM customers WHERE lower(btrim(name)) = lower(btrim(:name))"
              + " AND lower(btrim(tax_number)) = lower(btrim(:taxNumber))"
              + " AND lifecycle_status <> 'ANONYMIZED' ORDER BY created_at LIMIT 1")
  Optional<UUID> findIdByNameAndTaxNumber(
      @Param("name") String name, @Param("taxNumber") String taxNumber);

  /**
   * A client with this name and ID number — the take-on commit re-check. Both compared trimmed and
   * case-insensitively, exactly as the dry run's name key does.
   */
  @Query(
      nativeQuery = true,
      value =
          "SELECT id FROM customers WHERE lower(btrim(name)) = lower(btrim(:name))"
              + " AND lower(btrim(id_number)) = lower(btrim(:idNumber))"
              + " AND lifecycle_status <> 'ANONYMIZED' ORDER BY created_at LIMIT 1")
  Optional<UUID> findIdByNameAndIdNumber(
      @Param("name") String name, @Param("idNumber") String idNumber);

  /**
   * The client, row-locked ({@code FOR UPDATE}): the one guard that serialises anonymisation with
   * the take-on's portal-access creation (Phase 93 655A), so a portal contact is never written with
   * a name or email that anonymisation has already removed, and anonymisation never misses a
   * contact being created.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Customer c WHERE c.id = :id")
  Optional<Customer> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Every email address the client is recorded as having held: the current one, plus the {@code
   * email} of its {@code customer.created} audit row and the from/to of every {@code
   * customer.updated} email change. Anonymisation reads it before overwriting the email, so the
   * take-on rows keyed by an earlier address are redacted too (Phase 93 655A, POPIA §17.6.5).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT DISTINCT h.email FROM (
            SELECT c.email FROM customers c WHERE c.id = :id
            UNION ALL
            SELECT a.details ->> 'email' FROM audit_events a
             WHERE a.entity_type = 'customer' AND a.entity_id = :id
               AND jsonb_typeof(a.details -> 'email') = 'string'
            UNION ALL
            SELECT d.v FROM audit_events a
             CROSS JOIN LATERAL (VALUES (a.details -> 'email' ->> 'from'),
                                        (a.details -> 'email' ->> 'to')) AS d(v)
             WHERE a.entity_type = 'customer' AND a.entity_id = :id
               AND jsonb_typeof(a.details -> 'email') = 'object'
          ) h(email)
           WHERE h.email IS NOT NULL AND h.email <> ''""")
  List<String> findEmailsEverHeld(@Param("id") UUID id);
}
