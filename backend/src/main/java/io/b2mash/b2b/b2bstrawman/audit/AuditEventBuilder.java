package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Builder that constructs an {@link AuditEventRecord}. Auto-populates actor, source, IP address,
 * and user agent from the current request context when available.
 *
 * <p>Required fields: {@code eventType}, {@code entityType}, {@code entityId}. Optional fields can
 * be set explicitly to override auto-population.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * AuditEventRecord record = AuditEventBuilder.builder()
 *     .eventType("project.created")
 *     .entityType("project")
 *     .entityId(project.getId())
 *     .details(Map.of("name", project.getName()))
 *     .build();
 * }</pre>
 */
public class AuditEventBuilder {

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

  /** Creates a new builder instance. */
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
   * Builds the {@link AuditEventRecord}, auto-populating fields from the current request context
   * where not explicitly set:
   *
   * <ul>
   *   <li>{@code actorId} from {@link RequestScopes#MEMBER_ID} if bound
   *   <li>{@code actorType} = "USER" if MEMBER_ID bound, "SYSTEM" otherwise
   *   <li>{@code source} = "API" if in HTTP request context, "INTERNAL" otherwise
   *   <li>{@code ipAddress} from {@code HttpServletRequest.getRemoteAddr()}
   *   <li>{@code userAgent} from {@code User-Agent} header (truncated to 500 chars)
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

    HttpServletRequest request = resolveHttpRequest();

    String resolvedSource = this.source;
    if (!sourceExplicitlySet) {
      resolvedSource = request != null ? "API" : "INTERNAL";
    }

    String resolvedIpAddress = null;
    String resolvedUserAgent = null;
    if (request != null) {
      resolvedIpAddress = request.getRemoteAddr();
      String ua = request.getHeader("User-Agent");
      if (ua != null && ua.length() > MAX_USER_AGENT_LENGTH) {
        ua = ua.substring(0, MAX_USER_AGENT_LENGTH);
      }
      resolvedUserAgent = ua;
    }

    return new AuditEventRecord(
        eventType,
        entityType,
        entityId,
        resolvedActorId,
        resolvedActorType,
        resolvedSource,
        resolvedIpAddress,
        resolvedUserAgent,
        details);
  }

  private static HttpServletRequest resolveHttpRequest() {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes servletAttrs) {
      return servletAttrs.getRequest();
    }
    return null;
  }
}
