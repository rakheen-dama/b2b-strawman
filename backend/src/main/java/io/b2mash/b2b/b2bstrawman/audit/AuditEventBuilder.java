package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Convenience builder for constructing {@link AuditEventRecord} instances. Auto-populates actor,
 * source, and HTTP request metadata from the current request context.
 *
 * <p>Required fields: {@code eventType}, {@code entityType}, {@code entityId}. All other fields
 * have sensible defaults derived from {@link RequestScopes} and {@link RequestContextHolder}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * auditService.log(AuditEventBuilder.builder()
 *     .eventType("task.claimed")
 *     .entityType("task")
 *     .entityId(taskId)
 *     .details(Map.of("assignee_id", memberId.toString()))
 *     .build());
 * }</pre>
 */
public final class AuditEventBuilder {

  private static final int MAX_USER_AGENT_LENGTH = 500;

  private String eventType;
  private String entityType;
  private UUID entityId;
  private UUID actorId;
  private String actorType;
  private String source;
  private Map<String, Object> details;

  private boolean actorIdExplicitlySet;
  private boolean actorTypeExplicitlySet;
  private boolean sourceExplicitlySet;

  private AuditEventBuilder() {}

  public static AuditEventBuilder builder() {
    return new AuditEventBuilder();
  }

  public AuditEventBuilder eventType(String eventType) {
    this.eventType = eventType;
    return this;
  }

  public AuditEventBuilder entityType(String entityType) {
    this.entityType = entityType;
    return this;
  }

  public AuditEventBuilder entityId(UUID entityId) {
    this.entityId = entityId;
    return this;
  }

  public AuditEventBuilder actorId(UUID actorId) {
    this.actorId = actorId;
    this.actorIdExplicitlySet = true;
    return this;
  }

  public AuditEventBuilder actorType(String actorType) {
    this.actorType = actorType;
    this.actorTypeExplicitlySet = true;
    return this;
  }

  public AuditEventBuilder source(String source) {
    this.source = source;
    this.sourceExplicitlySet = true;
    return this;
  }

  public AuditEventBuilder details(Map<String, Object> details) {
    this.details = details;
    return this;
  }

  /**
   * Builds the {@link AuditEventRecord}, auto-populating fields from request context:
   *
   * <ul>
   *   <li>{@code actorId}: from {@code RequestScopes.MEMBER_ID} if bound and not explicitly set
   *   <li>{@code actorType}: "USER" if MEMBER_ID bound, "SYSTEM" otherwise (unless explicitly set)
   *   <li>{@code source}: "API" if in HTTP context, "INTERNAL" otherwise (unless explicitly set)
   *   <li>{@code ipAddress}: from {@code HttpServletRequest.getRemoteAddr()} (null if not HTTP)
   *   <li>{@code userAgent}: from User-Agent header, truncated to 500 chars (null if not HTTP)
   * </ul>
   */
  public AuditEventRecord build() {
    UUID resolvedActorId = this.actorId;
    if (!actorIdExplicitlySet && RequestScopes.MEMBER_ID.isBound()) {
      resolvedActorId = RequestScopes.MEMBER_ID.get();
    }

    String resolvedActorType = this.actorType;
    if (!actorTypeExplicitlySet) {
      resolvedActorType = RequestScopes.MEMBER_ID.isBound() ? "USER" : "SYSTEM";
    }

    String resolvedSource = this.source;
    if (!sourceExplicitlySet) {
      resolvedSource = RequestContextHolder.getRequestAttributes() != null ? "API" : "INTERNAL";
    }

    String ipAddress = null;
    String userAgent = null;
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes servletAttrs) {
      HttpServletRequest request = servletAttrs.getRequest();
      ipAddress = request.getRemoteAddr();
      String ua = request.getHeader("User-Agent");
      if (ua != null && ua.length() > MAX_USER_AGENT_LENGTH) {
        ua = ua.substring(0, MAX_USER_AGENT_LENGTH);
      }
      userAgent = ua;
    }

    return new AuditEventRecord(
        eventType,
        entityType,
        entityId,
        resolvedActorId,
        resolvedActorType,
        resolvedSource,
        ipAddress,
        userAgent,
        details);
  }
}
