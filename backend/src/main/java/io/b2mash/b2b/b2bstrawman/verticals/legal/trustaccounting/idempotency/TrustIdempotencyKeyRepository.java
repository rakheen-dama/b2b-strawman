package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrustIdempotencyKeyRepository extends JpaRepository<TrustIdempotencyKey, UUID> {

  /**
   * Claims {@code key}: 1 when this call inserted the IN_PROGRESS row, 0 when the key already
   * exists. A concurrent claim of the same key waits on the primary key until the other claim's
   * short transaction commits, then reads 0 — so exactly one request ever owns a key.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO trust_idempotency_keys
              (idempotency_key, member_id, http_method, route_template, request_hash, state,
               created_at)
          VALUES (:key, :memberId, :method, :route, :hash, 'IN_PROGRESS', :now)
          ON CONFLICT (idempotency_key) DO NOTHING
          """,
      nativeQuery = true)
  int claim(
      @Param("key") UUID key,
      @Param("memberId") UUID memberId,
      @Param("method") String method,
      @Param("route") String route,
      @Param("hash") String hash,
      @Param("now") Instant now);

  /** Stores the response on a claim this request still owns (IN_PROGRESS); 1 when stored. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE trust_idempotency_keys
             SET state = 'COMPLETED', response_status = :status, response_body = :body,
                 response_headers = CAST(:headers AS jsonb), completed_at = :now
           WHERE idempotency_key = :key AND state = 'IN_PROGRESS'
          """,
      nativeQuery = true)
  int complete(
      @Param("key") UUID key,
      @Param("status") int status,
      @Param("body") String body,
      @Param("headers") String headersJson,
      @Param("now") Instant now);

  /** Deletes every key created before {@code cutoff}, whatever its state; the count deleted. */
  @Modifying
  @Query(
      value = "DELETE FROM trust_idempotency_keys WHERE created_at < :cutoff",
      nativeQuery = true)
  int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
