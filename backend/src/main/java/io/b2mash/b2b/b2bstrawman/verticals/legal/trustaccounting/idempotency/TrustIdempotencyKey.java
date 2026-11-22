package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One claimed {@code Idempotency-Key} of a trust write (648B F-11). Written only by {@link
 * TrustIdempotencyKeyRepository}'s native claim / complete statements; read here to decide a retry.
 */
@Entity
@Table(name = "trust_idempotency_keys")
public class TrustIdempotencyKey {

  /** Whether the first request's outcome is stored. */
  public enum State {
    IN_PROGRESS,
    COMPLETED
  }

  @Id
  @Column(name = "idempotency_key", nullable = false, updatable = false)
  private UUID idempotencyKey;

  @Column(name = "member_id", nullable = false, updatable = false)
  private UUID memberId;

  @Column(name = "http_method", nullable = false, updatable = false, length = 10)
  private String httpMethod;

  @Column(name = "route_template", nullable = false, updatable = false, length = 255)
  private String routeTemplate;

  @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 20)
  private State state;

  @Column(name = "response_status")
  private Integer responseStatus;

  @Column(name = "response_body", columnDefinition = "text")
  private String responseBody;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_headers", columnDefinition = "jsonb")
  private Map<String, String> responseHeaders;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected TrustIdempotencyKey() {}

  public UUID getIdempotencyKey() {
    return idempotencyKey;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public String getRouteTemplate() {
    return routeTemplate;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public State getState() {
    return state;
  }

  public Integer getResponseStatus() {
    return responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public Map<String, String> getResponseHeaders() {
    return responseHeaders;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
