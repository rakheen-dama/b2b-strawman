package io.b2mash.b2b.b2bstrawman.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogService;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.integration.email.RenderedEmail;
import io.b2mash.b2b.b2bstrawman.integration.email.SendResult;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

/**
 * GAP-L-51 — locks the "Send for Acceptance" email subject to include the {@code engagement letter}
 * keyword so the recipient's inbox preview reads as a legal-firm document request, not a generic
 * "Document for your acceptance: …pdf" attachment.
 *
 * <p>Uses Mockito + a package-private test so we can stand up the package-private {@link
 * AcceptanceNotificationService} without a full Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcceptanceNotificationServiceSubjectTest {

  @Mock private EmailTemplateRenderer emailTemplateRenderer;
  @Mock private EmailContextBuilder emailContextBuilder;
  @Mock private IntegrationRegistry integrationRegistry;
  @Mock private EmailDeliveryLogService deliveryLogService;
  @Mock private EmailRateLimiter emailRateLimiter;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private AuditService auditService;
  @Mock private EmailProvider emailProvider;

  @Test
  void sendAcceptanceEmail_useEngagementLetterKeywordInSubject() {
    var service =
        new AcceptanceNotificationService(
            emailTemplateRenderer,
            emailContextBuilder,
            integrationRegistry,
            deliveryLogService,
            emailRateLimiter,
            eventPublisher,
            auditService);

    // Base context must carry orgName so the service can render the new subject template.
    Map<String, Object> baseContext = new HashMap<>();
    baseContext.put("orgName", "Mathebula & Partners");
    when(emailContextBuilder.buildBaseContext(anyString(), any())).thenReturn(baseContext);

    when(integrationRegistry.resolve(eq(IntegrationDomain.EMAIL), eq(EmailProvider.class)))
        .thenReturn(emailProvider);
    when(emailProvider.providerId()).thenReturn("mailpit");
    when(emailRateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);

    // Capture whatever subject string was pushed into the renderer's context.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    when(emailTemplateRenderer.render(anyString(), captor.capture()))
        .thenReturn(new RenderedEmail("rendered-subject", "<html></html>", "plain"));
    when(emailProvider.sendEmail(any())).thenReturn(new SendResult(true, "msg-1", null));

    var contact = org.mockito.Mockito.mock(PortalContact.class);
    when(contact.getEmail()).thenReturn("sipho@example.com");
    when(contact.getDisplayName()).thenReturn("Sipho Dlamini");
    when(contact.getId()).thenReturn(UUID.randomUUID());

    var doc = org.mockito.Mockito.mock(GeneratedDocument.class);
    when(doc.getFileName())
        .thenReturn("engagement-letter-litigation-dlamini-v-road-accident-fund.pdf");

    var request = org.mockito.Mockito.mock(AcceptanceRequest.class);
    when(request.getRequestToken()).thenReturn("token-1");
    when(request.getExpiresAt()).thenReturn(Instant.now().plus(30, ChronoUnit.DAYS));
    when(request.getId()).thenReturn(UUID.randomUUID());

    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_testca1234ab")
        .run(
            () ->
                service.sendAcceptanceEmail(
                    request, contact, doc, "acceptance-request", "https://portal.example.com"));

    Map<String, Object> renderedCtx = captor.getValue();
    String subject = (String) renderedCtx.get("subject");
    assertThat(subject).contains("Mathebula & Partners");
    assertThat(subject).containsIgnoringCase("engagement letter");
    assertThat(subject).contains("engagement-letter-litigation-dlamini-v-road-accident-fund.pdf");
  }

  @Test
  void reminderAcceptanceEmail_alsoIncludesEngagementLetterKeyword() {
    var service =
        new AcceptanceNotificationService(
            emailTemplateRenderer,
            emailContextBuilder,
            integrationRegistry,
            deliveryLogService,
            emailRateLimiter,
            eventPublisher,
            auditService);

    Map<String, Object> baseContext = new HashMap<>();
    baseContext.put("orgName", "Mathebula & Partners");
    when(emailContextBuilder.buildBaseContext(anyString(), any())).thenReturn(baseContext);

    when(integrationRegistry.resolve(eq(IntegrationDomain.EMAIL), eq(EmailProvider.class)))
        .thenReturn(emailProvider);
    when(emailProvider.providerId()).thenReturn("mailpit");
    when(emailRateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    when(emailTemplateRenderer.render(anyString(), captor.capture()))
        .thenReturn(new RenderedEmail("rendered-subject", "<html></html>", "plain"));
    when(emailProvider.sendEmail(any())).thenReturn(new SendResult(true, "msg-2", null));

    var contact = org.mockito.Mockito.mock(PortalContact.class);
    when(contact.getEmail()).thenReturn("sipho@example.com");
    when(contact.getDisplayName()).thenReturn("Sipho Dlamini");
    when(contact.getId()).thenReturn(UUID.randomUUID());

    var doc = org.mockito.Mockito.mock(GeneratedDocument.class);
    when(doc.getFileName()).thenReturn("engagement-letter.pdf");

    var request = org.mockito.Mockito.mock(AcceptanceRequest.class);
    when(request.getRequestToken()).thenReturn("token-1");
    when(request.getExpiresAt()).thenReturn(Instant.now().plus(30, ChronoUnit.DAYS));
    when(request.getSentAt()).thenReturn(Instant.now().minus(3, ChronoUnit.DAYS));
    when(request.getId()).thenReturn(UUID.randomUUID());

    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_testca1234ab")
        .run(
            () ->
                service.sendAcceptanceEmail(
                    request, contact, doc, "acceptance-reminder", "https://portal.example.com"));

    Map<String, Object> renderedCtx = captor.getValue();
    String subject = (String) renderedCtx.get("subject");
    assertThat(subject).containsIgnoringCase("reminder");
    assertThat(subject).containsIgnoringCase("engagement letter");
  }
}
