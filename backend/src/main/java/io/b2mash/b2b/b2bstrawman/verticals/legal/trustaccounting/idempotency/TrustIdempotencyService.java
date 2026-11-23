package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * The claim / complete protocol behind {@link IdempotentTrustWrite} (648B F-11). Each step runs in
 * its own short transaction, committed before the write starts and after it ends, so no lock or
 * connection of the claim is held while the write runs, and the write's own transaction is exactly
 * what it was without a key.
 */
@Service
public class TrustIdempotencyService {

  /** The request header carrying the client-generated key (a UUID). */
  public static final String HEADER = "Idempotency-Key";

  /** Set on a replayed response so a client (and a test) can tell a replay from a first run. */
  public static final String REPLAYED_HEADER = "Idempotency-Replayed";

  /** How long a key is kept: a retry after this is a new request (purged daily). */
  public static final Duration RETENTION = Duration.ofDays(7);

  static final String REUSED_TITLE = "Idempotency key reused";
  static final String REUSED_DETAIL = "Idempotency key reused with a different request";
  static final String IN_PROGRESS_TITLE = "Request still in progress";
  static final String IN_PROGRESS_DETAIL =
      "An earlier submit of this entry is still in progress, or its outcome is unknown. Check the"
          + " ledger to see whether it was recorded; if it was not, choose 'Submit as a new"
          + " entry'.";

  /**
   * Machine-readable {@code code} of the two 409s, so a client can tell "outcome unknown, keep the
   * key" (in progress) from a refusal.
   */
  public static final String IN_PROGRESS_CODE = "idempotency_in_progress";

  public static final String REUSED_CODE = "idempotency_key_reused";

  private static final Logger log = LoggerFactory.getLogger(TrustIdempotencyService.class);

  private final TrustIdempotencyKeyRepository repository;
  private final TransactionTemplate ownTransaction;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  public TrustIdempotencyService(
      TrustIdempotencyKeyRepository repository,
      PlatformTransactionManager transactionManager,
      Clock clock,
      ObjectMapper objectMapper) {
    this.repository = repository;
    var template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.setName("TrustIdempotencyService");
    this.ownTransaction = template;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  /** The stored response of a completed key, replayed verbatim. */
  public record StoredResponse(int status, String body, Map<String, String> headers) {}

  /** What a claim decided: this request owns the key, or the stored response is replayed. */
  public sealed interface Claim {
    /** This request inserted the key and runs the write. */
    record Owned(UUID key) implements Claim {}

    /** The key's first request completed; its response is replayed and nothing runs. */
    record Replay(StoredResponse response) implements Claim {}
  }

  /** Parses the header value; a missing or non-UUID key is refused with 400. */
  public UUID parseKey(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      throw new InvalidStateException(
          "Idempotency-Key header required",
          "Every trust write needs an Idempotency-Key header (a UUID) so that a retry never posts"
              + " twice.");
    }
    try {
      var trimmed = headerValue.trim();
      var key = UUID.fromString(trimmed);
      // UUID.fromString accepts shortened groups ("1-1-1-1-1"); require the canonical form.
      if (!key.toString().equalsIgnoreCase(trimmed)) {
        throw new IllegalArgumentException("not canonical");
      }
      return key;
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException(
          "Invalid Idempotency-Key", "The Idempotency-Key header must be a UUID.");
    }
  }

  /**
   * Claims {@code key} for this request in its own transaction. Returns {@link Claim.Owned} when
   * this request inserted it, {@link Claim.Replay} when the key's first request completed with the
   * same member and the same request; otherwise refuses with 409 — a different member or request
   * ({@value #REUSED_DETAIL}), or a key still IN_PROGRESS (never re-executed).
   */
  public Claim claim(UUID key, UUID memberId, String method, String route, String requestHash) {
    return ownTransaction.execute(
        status -> {
          if (repository.claim(key, memberId, method, route, requestHash, Instant.now(clock))
              == 1) {
            return new Claim.Owned(key);
          }
          var existing =
              repository
                  .findById(key)
                  // Purged between the conflict and the read: its outcome is unknown.
                  .orElseThrow(() -> inProgress());
          if (!existing.getMemberId().equals(memberId)
              || !existing.getRequestHash().equals(requestHash)) {
            throw new ResourceConflictException(
                REUSED_TITLE, REUSED_DETAIL, Map.of("code", REUSED_CODE));
          }
          if (existing.getState() == TrustIdempotencyKey.State.COMPLETED) {
            return new Claim.Replay(
                new StoredResponse(
                    existing.getResponseStatus(),
                    existing.getResponseBody(),
                    existing.getResponseHeaders() == null
                        ? Map.of()
                        : existing.getResponseHeaders()));
          }
          throw inProgress();
        });
  }

  /**
   * Stores the response of the request that owns {@code key} and marks it COMPLETED, in its own
   * transaction. A failure here is logged and swallowed: the write already committed, the key stays
   * IN_PROGRESS, and a retry is refused with 409 rather than posting again.
   */
  public void complete(UUID key, StoredResponse response) {
    try {
      String headers = objectMapper.writeValueAsString(response.headers());
      ownTransaction.executeWithoutResult(
          status ->
              repository.complete(
                  key, response.status(), response.body(), headers, Instant.now(clock)));
    } catch (RuntimeException e) {
      log.error(
          "Trust idempotency key {} NOT completed (status {}); it stays IN_PROGRESS and a retry"
              + " is refused: {}",
          key,
          response.status(),
          e.getMessage(),
          e);
    }
  }

  private static ResourceConflictException inProgress() {
    return new ResourceConflictException(
        IN_PROGRESS_TITLE, IN_PROGRESS_DETAIL, Map.of("code", IN_PROGRESS_CODE));
  }

  /** Deletes this tenant's keys older than {@link #RETENTION}; the count deleted. */
  public int purgeExpired() {
    Instant cutoff = Instant.now(clock).minus(RETENTION);
    Integer deleted = ownTransaction.execute(status -> repository.deleteCreatedBefore(cutoff));
    return deleted == null ? 0 : deleted;
  }
}
