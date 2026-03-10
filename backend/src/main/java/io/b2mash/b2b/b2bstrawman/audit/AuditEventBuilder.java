package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.billingrun.BillingRun;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
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

  /** Convenience factory for billing_run.created audit events. */
  public static AuditEventRecord billingRunCreated(BillingRun run) {
    return builder()
        .eventType("billing_run.created")
        .entityType("billing_run")
        .entityId(run.getId())
        .details(
            Map.of(
                "name", run.getName() != null ? run.getName() : "",
                "period_from", run.getPeriodFrom().toString(),
                "period_to", run.getPeriodTo().toString(),
                "currency", run.getCurrency()))
        .build();
  }

  /** Convenience factory for billing_run.cancelled audit events. */
  public static AuditEventRecord billingRunCancelled(BillingRun run, int voidedInvoiceCount) {
    return builder()
        .eventType("billing_run.cancelled")
        .entityType("billing_run")
        .entityId(run.getId())
        .details(
            Map.of(
                "name",
                run.getName() != null ? run.getName() : "",
                "period_from",
                run.getPeriodFrom().toString(),
                "period_to",
                run.getPeriodTo().toString(),
                "voided_invoice_count",
                voidedInvoiceCount))
        .build();
  }

  /** Convenience factory for billing_run.generated audit events. */
  public static AuditEventRecord billingRunGenerated(
      BillingRun run, int invoiceCount, int failedCount) {
    return builder()
        .eventType("billing_run.generated")
        .entityType("billing_run")
        .entityId(run.getId())
        .details(
            Map.of(
                "name",
                run.getName() != null ? run.getName() : "",
                "period_from",
                run.getPeriodFrom().toString(),
                "period_to",
                run.getPeriodTo().toString(),
                "invoice_count",
                invoiceCount,
                "failed_count",
                failedCount))
        .build();
  }

  /** Convenience factory for billing_run.approved audit events. */
  public static AuditEventRecord billingRunApproved(BillingRun run, int approvedCount) {
    return builder()
        .eventType("billing_run.approved")
        .entityType("billing_run")
        .entityId(run.getId())
        .details(
            Map.of(
                "name",
                run.getName() != null ? run.getName() : "",
                "approved_count",
                approvedCount))
        .build();
  }

  /** Convenience factory for billing_run.sent audit events. */
  public static AuditEventRecord billingRunSent(BillingRun run, int sentCount) {
    return builder()
        .eventType("billing_run.sent")
        .entityType("billing_run")
        .entityId(run.getId())
        .details(
            Map.of(
                "name",
                run.getName() != null ? run.getName() : "",
                "sent_count",
                sentCount,
                "total_amount",
                run.getTotalAmount() != null ? run.getTotalAmount().toString() : "0"))
        .build();
  }

  /** Convenience factory for role.created audit events. */
  public static AuditEventRecord roleCreated(OrgRole role) {
    return builder()
        .eventType("role.created")
        .entityType("org_role")
        .entityId(role.getId())
        .details(
            Map.of(
                "name",
                role.getName(),
                "slug",
                role.getSlug(),
                "capabilities",
                role.getCapabilities().stream()
                    .map(io.b2mash.b2b.b2bstrawman.orgrole.Capability::name)
                    .sorted()
                    .toList()))
        .build();
  }

  /** Convenience factory for role.updated audit events. */
  public static AuditEventRecord roleUpdated(
      OrgRole role, Set<String> addedCaps, Set<String> removedCaps, long affectedMemberCount) {
    return builder()
        .eventType("role.updated")
        .entityType("org_role")
        .entityId(role.getId())
        .details(
            Map.of(
                "name", role.getName(),
                "addedCapabilities", addedCaps.stream().sorted().toList(),
                "removedCapabilities", removedCaps.stream().sorted().toList(),
                "affectedMemberCount", affectedMemberCount))
        .build();
  }

  /** Convenience factory for role.deleted audit events. */
  public static AuditEventRecord roleDeleted(OrgRole role) {
    return builder()
        .eventType("role.deleted")
        .entityType("org_role")
        .entityId(role.getId())
        .details(
            Map.of(
                "name", role.getName(),
                "slug", role.getSlug()))
        .build();
  }

  /** Convenience factory for member.role_changed audit events. */
  public static AuditEventRecord memberRoleChanged(
      Member member, String previousRole, String newRole, Set<String> overrides) {
    return builder()
        .eventType("member.role_changed")
        .entityType("member")
        .entityId(member.getId())
        .details(
            Map.of(
                "memberId", member.getId().toString(),
                "memberName", member.getName() != null ? member.getName() : "",
                "previousRole", previousRole != null ? previousRole : "",
                "newRole", newRole != null ? newRole : "",
                "overrides", overrides.stream().sorted().toList()))
        .build();
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
   *
   * @throws IllegalStateException if eventType, entityType, or entityId is null
   */
  public AuditEventRecord build() {
    if (eventType == null || entityType == null || entityId == null) {
      throw new IllegalStateException(
          "AuditEventBuilder requires eventType, entityType, and entityId; missing: "
              + (eventType == null ? "eventType " : "")
              + (entityType == null ? "entityType " : "")
              + (entityId == null ? "entityId" : ""));
    }

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

    if (resolvedActorType == null || resolvedSource == null) {
      throw new IllegalStateException(
          "AuditEventBuilder failed to resolve required fields: "
              + (resolvedActorType == null ? "actorType " : "")
              + (resolvedSource == null ? "source" : ""));
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
