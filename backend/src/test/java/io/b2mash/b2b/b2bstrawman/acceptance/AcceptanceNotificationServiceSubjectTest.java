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
import io.b2mash.b2b.b2bstrawman.portal.PortalLinkBuilder;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
 * <p>G-B16 (Phase 88 626G) — the subject names the generated document's template: "engagement
 * letter" for an {@code ENGAGEMENT_LETTER} template, otherwise the template name in lower case
 * ("offer to purchase"), and "document" when no template resolves.
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
  @Mock private DocumentTemplateRepository documentTemplateRepository;

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
            auditService,
            new PortalLinkBuilder("https://portal.example.com"),
            documentTemplateRepository);

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
    stubTemplate(doc, engagementLetterTemplate());

    var request = org.mockito.Mockito.mock(AcceptanceRequest.class);
    when(request.getRequestToken()).thenReturn("token-1");
    when(request.getExpiresAt()).thenReturn(Instant.now().plus(30, ChronoUnit.DAYS));
    when(request.getId()).thenReturn(UUID.randomUUID());

    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_testca1234ab")
        .run(() -> service.sendAcceptanceEmail(request, contact, doc, "acceptance-request"));

    Map<String, Object> renderedCtx = captor.getValue();
    String subject = (String) renderedCtx.get("subject");
    assertThat(subject).contains("Mathebula & Partners");
    assertThat(subject).containsIgnoringCase("engagement letter");
    assertThat(subject).contains("engagement-letter-litigation-dlamini-v-road-accident-fund.pdf");
    // Phase 88 U11 (§13.6.6): the acceptance link is the public /accept/{token} page — no orgId.
    assertThat(renderedCtx.get("acceptanceUrl"))
        .isEqualTo("https://portal.example.com/accept/token-1");
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
            auditService,
            new PortalLinkBuilder("https://portal.example.com"),
            documentTemplateRepository);

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
    stubTemplate(doc, engagementLetterTemplate());

    var request = org.mockito.Mockito.mock(AcceptanceRequest.class);
    when(request.getRequestToken()).thenReturn("token-1");
    when(request.getExpiresAt()).thenReturn(Instant.now().plus(30, ChronoUnit.DAYS));
    when(request.getSentAt()).thenReturn(Instant.now().minus(3, ChronoUnit.DAYS));
    when(request.getId()).thenReturn(UUID.randomUUID());

    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_testca1234ab")
        .run(() -> service.sendAcceptanceEmail(request, contact, doc, "acceptance-reminder"));

    Map<String, Object> renderedCtx = captor.getValue();
    String subject = (String) renderedCtx.get("subject");
    assertThat(subject).containsIgnoringCase("reminder");
    assertThat(subject).containsIgnoringCase("engagement letter");
  }

  // --- G-B16 (Phase 88 626G): the subject names the document's template ---

  private static final String OTP_FILE_NAME =
      "offer-to-purchase-khumalo-property-holdings-2026-10-28.pdf";

  @Test
  void sendAcceptanceEmail_offerToPurchase_namesTheTemplateInTheSubject() {
    String subject = subjectFor(offerToPurchaseTemplate(), OTP_FILE_NAME, false);

    assertThat(subject)
        .isEqualTo(
            "Mathebula & Partners -- Please review offer to purchase for acceptance: "
                + OTP_FILE_NAME);
    assertThat(subject).doesNotContainIgnoringCase("engagement letter");
  }

  @Test
  void reminder_offerToPurchase_namesTheTemplate() {
    String subject = subjectFor(offerToPurchaseTemplate(), OTP_FILE_NAME, true);

    assertThat(subject)
        .isEqualTo(
            "Reminder: Mathebula & Partners -- Please review offer to purchase for acceptance");
  }

  @Test
  void sendAcceptanceEmail_engagementLetterCategory_keepsTheEngagementLetterWording() {
    String subject =
        subjectFor(engagementLetterTemplate(), "engagement-letter-litigation-dlamini.pdf", false);

    assertThat(subject)
        .isEqualTo(
            "Mathebula & Partners -- Please review engagement letter for acceptance:"
                + " engagement-letter-litigation-dlamini.pdf");
  }

  @Test
  void subject_fallsBackToDocument_whenTheTemplateDoesNotResolve() {
    String subject = subjectFor(null, OTP_FILE_NAME, false);

    assertThat(subject)
        .isEqualTo(
            "Mathebula & Partners -- Please review document for acceptance: " + OTP_FILE_NAME);
  }

  @Test
  void subject_fallsBackToDocument_whenTheTemplateIdIsNull() {
    var doc = org.mockito.Mockito.mock(GeneratedDocument.class);
    when(doc.getTemplateId()).thenReturn(null);

    assertThat(newService().documentLabel(doc)).isEqualTo("document");
    org.mockito.Mockito.verifyNoInteractions(documentTemplateRepository);
  }

  @Test
  void subject_fallsBackToDocument_whenTheTemplateNameIsBlank() {
    var blank =
        new DocumentTemplate(
            TemplateEntityType.PROJECT, "   ", "blank-name", TemplateCategory.OTHER, Map.of());

    assertThat(subjectFor(blank, OTP_FILE_NAME, false))
        .isEqualTo(
            "Mathebula & Partners -- Please review document for acceptance: " + OTP_FILE_NAME);
  }

  /**
   * Sends one acceptance (or reminder) email for a document whose template is {@code template}
   * ({@code null} = the template id does not resolve) and returns the subject put in the context.
   */
  private String subjectFor(DocumentTemplate template, String fileName, boolean reminder) {
    var service = newService();

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
    when(emailProvider.sendEmail(any())).thenReturn(new SendResult(true, "msg-3", null));

    var contact = org.mockito.Mockito.mock(PortalContact.class);
    when(contact.getEmail()).thenReturn("sipho@example.com");
    when(contact.getDisplayName()).thenReturn("Sipho Dlamini");
    when(contact.getId()).thenReturn(UUID.randomUUID());

    var doc = org.mockito.Mockito.mock(GeneratedDocument.class);
    when(doc.getFileName()).thenReturn(fileName);
    stubTemplate(doc, template);

    var request = org.mockito.Mockito.mock(AcceptanceRequest.class);
    when(request.getRequestToken()).thenReturn("token-1");
    when(request.getExpiresAt()).thenReturn(Instant.now().plus(30, ChronoUnit.DAYS));
    if (reminder) {
      when(request.getSentAt()).thenReturn(Instant.now().minus(3, ChronoUnit.DAYS));
    }
    when(request.getId()).thenReturn(UUID.randomUUID());

    String emailTemplate = reminder ? "acceptance-reminder" : "acceptance-request";
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_testca1234ab")
        .run(() -> service.sendAcceptanceEmail(request, contact, doc, emailTemplate));

    return (String) captor.getValue().get("subject");
  }

  private AcceptanceNotificationService newService() {
    return new AcceptanceNotificationService(
        emailTemplateRenderer,
        emailContextBuilder,
        integrationRegistry,
        deliveryLogService,
        emailRateLimiter,
        eventPublisher,
        auditService,
        new PortalLinkBuilder("https://portal.example.com"),
        documentTemplateRepository);
  }

  /** Stubs {@code doc.getTemplateId()} and its lookup; {@code null} template = not found. */
  private void stubTemplate(GeneratedDocument doc, DocumentTemplate template) {
    UUID templateId = UUID.randomUUID();
    when(doc.getTemplateId()).thenReturn(templateId);
    when(documentTemplateRepository.findById(templateId)).thenReturn(Optional.ofNullable(template));
  }

  private static DocumentTemplate engagementLetterTemplate() {
    return new DocumentTemplate(
        TemplateEntityType.PROJECT,
        "Engagement Letter — Litigation",
        "engagement-letter-litigation",
        TemplateCategory.ENGAGEMENT_LETTER,
        Map.of());
  }

  /** legal-za "Offer to Purchase" is category OTHER (626G reproduction, before/README.md). */
  private static DocumentTemplate offerToPurchaseTemplate() {
    return new DocumentTemplate(
        TemplateEntityType.PROJECT,
        "Offer to Purchase",
        "offer-to-purchase",
        TemplateCategory.OTHER,
        Map.of());
  }
}
