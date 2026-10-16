package io.b2mash.b2b.b2bstrawman.informationrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Locks the client-facing "View Request" link in the follow-up information-request emails
 * (item-accepted, item-rejected, reminder, completed) to the client portal's request detail page.
 *
 * <p>These emails previously linked to {@code <staff-app>/portal}, the legacy in-app portal page
 * removed in Phase 86 (Epic 597) — that link would 404 for the client. The sibling {@code
 * request-sent} email was already moved to the portal host in QA cycle #1107.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InformationRequestEmailServicePortalUrlTest {

  private static final String PORTAL_BASE_URL = "https://portal.example.com";
  private static final String TENANT = "tenant_irportal1234";

  @Mock private IntegrationRegistry integrationRegistry;
  @Mock private EmailTemplateRenderer emailTemplateRenderer;
  @Mock private EmailContextBuilder emailContextBuilder;
  @Mock private EmailDeliveryLogService deliveryLogService;
  @Mock private EmailRateLimiter emailRateLimiter;
  @Mock private EmailProvider emailProvider;

  private InformationRequestEmailService service;
  private ArgumentCaptor<Map<String, Object>> contextCaptor;
  private final UUID requestId = UUID.randomUUID();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    service =
        new InformationRequestEmailService(
            integrationRegistry,
            emailTemplateRenderer,
            emailContextBuilder,
            deliveryLogService,
            emailRateLimiter,
            PORTAL_BASE_URL);

    when(emailContextBuilder.buildBaseContext(anyString(), any()))
        .thenAnswer(
            inv -> {
              Map<String, Object> ctx = new HashMap<>();
              ctx.put("orgName", "Mathebula & Partners");
              return ctx;
            });
    when(integrationRegistry.resolve(eq(IntegrationDomain.EMAIL), eq(EmailProvider.class)))
        .thenReturn(emailProvider);
    when(emailProvider.providerId()).thenReturn("mailpit");
    when(emailRateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
    when(emailProvider.sendEmail(any())).thenReturn(new SendResult(true, "msg-1", null));

    contextCaptor = ArgumentCaptor.forClass(Map.class);
    when(emailTemplateRenderer.render(anyString(), contextCaptor.capture()))
        .thenReturn(new RenderedEmail("subject", "<html></html>", "plain"));
  }

  @Test
  void itemAcceptedEmail_linksToPortalRequestDetail() {
    ScopedValue.where(RequestScopes.TENANT_ID, TENANT)
        .run(
            () ->
                service.sendItemAcceptedEmail(
                    "client@example.com", "Sipho", "ID copy", "REQ-0001", requestId));

    assertPortalDeepLink();
  }

  @Test
  void itemRejectedEmail_linksToPortalRequestDetail() {
    ScopedValue.where(RequestScopes.TENANT_ID, TENANT)
        .run(
            () ->
                service.sendItemRejectedEmail(
                    "client@example.com", "Sipho", "ID copy", "REQ-0001", "Blurry", requestId));

    assertPortalDeepLink();
  }

  @Test
  void reminderEmail_linksToPortalRequestDetail() {
    var item = mock(RequestItem.class);
    when(item.getName()).thenReturn("ID copy");
    when(item.getStatus()).thenReturn(ItemStatus.PENDING);

    ScopedValue.where(RequestScopes.TENANT_ID, TENANT)
        .run(
            () ->
                service.sendReminderEmail(
                    "client@example.com", "Sipho", "REQ-0001", List.of(item), requestId));

    assertPortalDeepLink();
  }

  @Test
  void requestCompletedEmail_linksToPortalRequestDetail() {
    ScopedValue.where(RequestScopes.TENANT_ID, TENANT)
        .run(
            () ->
                service.sendRequestCompletedEmail(
                    "client@example.com", "Sipho", "REQ-0001", 3, requestId));

    assertPortalDeepLink();
  }

  private void assertPortalDeepLink() {
    assertThat(contextCaptor.getValue().get("portalUrl"))
        .as("client email 'View Request' link must deep-link into the client portal")
        .isEqualTo(PORTAL_BASE_URL + "/requests/" + requestId);
  }
}
