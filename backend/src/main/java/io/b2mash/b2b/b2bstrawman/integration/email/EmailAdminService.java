package io.b2mash.b2b.b2bstrawman.integration.email;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service encapsulating admin email business logic: delivery log listing, stats aggregation, and
 * test email sending.
 */
@Service
public class EmailAdminService {

  private static final Logger log = LoggerFactory.getLogger(EmailAdminService.class);

  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final IntegrationRegistry integrationRegistry;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final MemberRepository memberRepository;
  private final AuditService auditService;

  public EmailAdminService(
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      IntegrationRegistry integrationRegistry,
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      MemberRepository memberRepository,
      AuditService auditService) {
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.integrationRegistry = integrationRegistry;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.memberRepository = memberRepository;
    this.auditService = auditService;
  }

  /**
   * Returns paginated delivery log entries, optionally filtered by status and date range. Defaults
   * to the last 30 days if no date range is provided.
   */
  public Page<EmailDeliveryLogResponse> getDeliveryLog(
      EmailDeliveryStatus status, Instant from, Instant to, Pageable pageable) {
    Instant effectiveFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
    Instant effectiveTo = to != null ? to : Instant.now();
    return deliveryLogService
        .findByFilters(status, effectiveFrom, effectiveTo, pageable)
        .map(EmailDeliveryLogResponse::from);
  }

  /**
   * Returns aggregated delivery statistics including current hour rate-limit usage from the
   * in-memory rate limiter.
   */
  public EmailDeliveryStats getStats() {
    EmailProvider provider =
        integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);
    String tenantSchema = RequestScopes.requireTenantId();
    var rateLimitStatus = emailRateLimiter.getStatus(tenantSchema, provider.providerId());
    return deliveryLogService.getStats(provider.providerId(), rateLimitStatus.limit());
  }

  /**
   * Sends a test email to the current user, records the delivery, and publishes an audit event.
   * Returns the delivery log entry as the response.
   */
  public EmailDeliveryLogResponse sendTestEmail() {
    var memberId = RequestScopes.requireMemberId();
    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    EmailProvider provider =
        integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

    String tenantSchema = RequestScopes.requireTenantId();
    if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
      throw new InvalidStateException(
          "Rate Limit Exceeded", "Email rate limit exceeded. Try again later.");
    }

    Map<String, Object> context = emailContextBuilder.buildBaseContext(member.getName(), null);
    var rendered = emailTemplateRenderer.render("test-email", context);

    var metadata = new HashMap<>(Map.of("referenceType", "TEST", "tenantSchema", tenantSchema));
    var message =
        new EmailMessage(
            member.getEmail(),
            rendered.subject(),
            rendered.htmlBody(),
            rendered.plainTextBody(),
            null,
            metadata);

    var result = provider.sendEmail(message);

    var deliveryLog =
        deliveryLogService.record(
            "TEST", null, "test-email", member.getEmail(), provider.providerId(), result);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("email.test.sent")
            .entityType("email")
            .entityId(deliveryLog.getId())
            .details(
                Map.of(
                    "recipientEmail", member.getEmail(),
                    "providerSlug", provider.providerId()))
            .build());

    log.debug(
        "Test email sent to {} via provider={}, success={}",
        member.getEmail(),
        provider.providerId(),
        result.success());

    return EmailDeliveryLogResponse.from(deliveryLog);
  }
}
