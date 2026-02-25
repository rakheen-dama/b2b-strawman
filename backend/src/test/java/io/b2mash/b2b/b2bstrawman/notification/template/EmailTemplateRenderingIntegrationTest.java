package io.b2mash.b2b.b2bstrawman.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.integration.email.RenderedEmail;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Rendering integration tests for all per-type email templates. Verifies that each template renders
 * successfully with representative context, produces non-empty HTML containing key variables, has a
 * subject populated, and generates a plain-text fallback.
 */
class EmailTemplateRenderingIntegrationTest {

  private EmailTemplateRenderer renderer;

  @BeforeEach
  void setUp() {
    renderer = new EmailTemplateRenderer();
  }

  @Test
  void render_notification_task_contains_task_details() {
    var context = buildBaseContext("Task assigned: Design Homepage");
    context.put("actorName", "Alice Smith");
    context.put("taskName", "Design Homepage");
    context.put("projectName", "Website Redesign");
    context.put("action", "assigned");
    context.put("taskUrl", "https://app.example.com/tasks/123");

    RenderedEmail result = renderer.render("notification-task", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("Task assigned: Design Homepage");
    assertThat(result.htmlBody()).contains("Alice Smith");
    assertThat(result.htmlBody()).contains("Design Homepage");
    assertThat(result.htmlBody()).contains("Website Redesign");
    assertThat(result.htmlBody()).contains("assigned");
    assertThat(result.htmlBody()).contains("https://app.example.com/tasks/123");
    assertThat(result.plainTextBody()).isNotEmpty();
    assertThat(result.plainTextBody()).contains("Design Homepage");
  }

  @Test
  void render_notification_comment_contains_comment_preview() {
    var context = buildBaseContext("New comment on Design Homepage");
    context.put("actorName", "Bob Jones");
    context.put(
        "commentPreview", "I think we should use a darker shade for the header background.");
    context.put("entityName", "Design Homepage");
    context.put("entityUrl", "https://app.example.com/tasks/123");

    RenderedEmail result = renderer.render("notification-comment", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("New comment on Design Homepage");
    assertThat(result.htmlBody()).contains("Bob Jones");
    assertThat(result.htmlBody()).contains("darker shade");
    assertThat(result.htmlBody()).contains("Design Homepage");
    assertThat(result.htmlBody()).contains("https://app.example.com/tasks/123");
    assertThat(result.plainTextBody()).isNotEmpty();
  }

  @Test
  void render_notification_document_contains_document_details() {
    var context = buildBaseContext("New document: Q4 Report");
    context.put("actorName", "Carol White");
    context.put("documentName", "Q4 Report");
    context.put("projectName", "Annual Reports");
    context.put("documentUrl", "https://app.example.com/documents/456");

    RenderedEmail result = renderer.render("notification-document", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("New document: Q4 Report");
    assertThat(result.htmlBody()).contains("Carol White");
    assertThat(result.htmlBody()).contains("Q4 Report");
    assertThat(result.htmlBody()).contains("Annual Reports");
    assertThat(result.htmlBody()).contains("https://app.example.com/documents/456");
    assertThat(result.plainTextBody()).isNotEmpty();
  }

  @Test
  void render_notification_member_contains_invitation_details() {
    var context = buildBaseContext("You've been invited to Acme Corp");
    context.put("inviterName", "Dave Manager");
    context.put("orgName", "Acme Corp");
    context.put("joinUrl", "https://app.example.com/invite/abc123");

    RenderedEmail result = renderer.render("notification-member", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("You've been invited to Acme Corp");
    assertThat(result.htmlBody()).contains("Dave Manager");
    assertThat(result.htmlBody()).contains("Acme Corp");
    assertThat(result.htmlBody()).contains("https://app.example.com/invite/abc123");
    assertThat(result.plainTextBody()).isNotEmpty();
  }

  @Test
  void render_notification_budget_contains_budget_alert() {
    var context = buildBaseContext("Budget alert: Website Redesign");
    context.put("projectName", "Website Redesign");
    context.put("budgetPercentage", "85%");
    context.put("threshold", "80%");
    context.put("projectUrl", "https://app.example.com/projects/789");

    RenderedEmail result = renderer.render("notification-budget", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("Budget alert: Website Redesign");
    assertThat(result.htmlBody()).contains("Website Redesign");
    assertThat(result.htmlBody()).contains("85%");
    assertThat(result.htmlBody()).contains("80%");
    assertThat(result.htmlBody()).contains("https://app.example.com/projects/789");
    assertThat(result.plainTextBody()).isNotEmpty();
  }

  @Test
  void render_notification_invoice_contains_invoice_details() {
    var context = buildBaseContext("Invoice INV-2024-0001 approved");
    context.put("invoiceNumber", "INV-2024-0001");
    context.put("customerName", "Widget Co");
    context.put("amount", "R 12,500.00");
    context.put("currency", "ZAR");
    context.put("status", "approved");
    context.put("invoiceUrl", "https://app.example.com/invoices/101");

    RenderedEmail result = renderer.render("notification-invoice", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("Invoice INV-2024-0001 approved");
    assertThat(result.htmlBody()).contains("INV-2024-0001");
    assertThat(result.htmlBody()).contains("Widget Co");
    assertThat(result.htmlBody()).contains("R 12,500.00");
    assertThat(result.htmlBody()).contains("approved");
    assertThat(result.htmlBody()).contains("https://app.example.com/invoices/101");
    assertThat(result.plainTextBody()).isNotEmpty();
  }

  @Test
  void render_notification_schedule_contains_schedule_details() {
    var context = buildBaseContext("Schedule update: Monthly Review");
    context.put("scheduleName", "Monthly Review");
    context.put("projectName", "Client Meetings");
    context.put("action", "created");
    context.put("scheduleUrl", "https://app.example.com/schedules/202");

    RenderedEmail result = renderer.render("notification-schedule", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("Schedule update: Monthly Review");
    assertThat(result.htmlBody()).contains("Monthly Review");
    assertThat(result.htmlBody()).contains("Client Meetings");
    assertThat(result.htmlBody()).contains("created");
    assertThat(result.htmlBody()).contains("https://app.example.com/schedules/202");
    assertThat(result.plainTextBody()).isNotEmpty();
  }

  @Test
  void render_notification_retainer_contains_retainer_details() {
    var context = buildBaseContext("Retainer update: Premium Support");
    context.put("retainerName", "Premium Support");
    context.put("customerName", "Big Corp");
    context.put("periodLabel", "February 2026");
    context.put("utilization", "75%");
    context.put("retainerUrl", "https://app.example.com/retainers/303");

    RenderedEmail result = renderer.render("notification-retainer", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("Retainer update: Premium Support");
    assertThat(result.htmlBody()).contains("Premium Support");
    assertThat(result.htmlBody()).contains("Big Corp");
    assertThat(result.htmlBody()).contains("February 2026");
    assertThat(result.htmlBody()).contains("75%");
    assertThat(result.htmlBody()).contains("https://app.example.com/retainers/303");
    assertThat(result.plainTextBody()).isNotEmpty();
  }

  @Test
  void render_portal_magic_link_contains_link_and_expiry() {
    var context = buildBaseContext("Access your portal");
    context.put("magicLinkUrl", "https://portal.example.com/auth?token=xyz789");
    context.put("expiryMinutes", 15);
    context.put("contactName", "Jane Client");
    // No unsubscribeUrl for transactional emails
    context.remove("unsubscribeUrl");

    RenderedEmail result = renderer.render("portal-magic-link", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("Access your portal");
    assertThat(result.htmlBody()).contains("https://portal.example.com/auth?token=xyz789");
    assertThat(result.htmlBody()).contains("15 minutes");
    assertThat(result.htmlBody()).contains("Jane Client");
    assertThat(result.htmlBody()).contains("Access Portal");
    assertThat(result.plainTextBody()).isNotEmpty();
    assertThat(result.plainTextBody()).contains("portal.example.com");
  }

  @Test
  void render_invoice_delivery_contains_invoice_and_portal_link() {
    var context = buildBaseContext("Invoice INV-2024-0042 from Acme Corp");
    context.put("invoiceNumber", "INV-2024-0042");
    context.put("amount", "R 8,750.00");
    context.put("currency", "ZAR");
    context.put("dueDate", "15 March 2026");
    context.put("customerName", "Widget Co");
    context.put("portalUrl", "https://portal.example.com/invoices/42");
    // No unsubscribeUrl for transactional emails
    context.remove("unsubscribeUrl");

    RenderedEmail result = renderer.render("invoice-delivery", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("Invoice INV-2024-0042 from Acme Corp");
    assertThat(result.htmlBody()).contains("INV-2024-0042");
    assertThat(result.htmlBody()).contains("R 8,750.00");
    assertThat(result.htmlBody()).contains("ZAR");
    assertThat(result.htmlBody()).contains("15 March 2026");
    assertThat(result.htmlBody()).contains("Widget Co");
    assertThat(result.htmlBody()).contains("https://portal.example.com/invoices/42");
    assertThat(result.plainTextBody()).isNotEmpty();
  }

  @Test
  void render_all_templates_with_minimal_context_does_not_throw() {
    // Verify LenientStandardDialect handles missing variables gracefully
    var minimalContext = new HashMap<String, Object>();
    minimalContext.put("subject", "Test");

    String[] templates = {
      "notification-task",
      "notification-comment",
      "notification-document",
      "notification-member",
      "notification-budget",
      "notification-invoice",
      "notification-schedule",
      "notification-retainer",
      "portal-magic-link",
      "invoice-delivery"
    };

    for (String template : templates) {
      RenderedEmail result = renderer.render(template, minimalContext);
      assertThat(result.htmlBody())
          .as("Template '%s' should render non-empty HTML with minimal context", template)
          .isNotEmpty();
      assertThat(result.subject())
          .as("Template '%s' should have subject 'Test'", template)
          .isEqualTo("Test");
      assertThat(result.plainTextBody())
          .as("Template '%s' should generate non-empty plain text", template)
          .isNotEmpty();
    }
  }

  @Test
  void render_notification_templates_include_brand_color_in_button() {
    var context = buildBaseContext("Test branding");
    context.put("brandColor", "#FF5733");
    context.put("taskUrl", "https://example.com");

    RenderedEmail result = renderer.render("notification-task", context);

    assertThat(result.htmlBody()).contains("#FF5733");
  }

  private Map<String, Object> buildBaseContext(String subject) {
    var context = new HashMap<String, Object>();
    context.put("orgName", "Test Org");
    context.put("orgLogoUrl", null);
    context.put("brandColor", "#2563EB");
    context.put("footerText", null);
    context.put("recipientName", "Alice");
    context.put("unsubscribeUrl", "https://app.example.com/unsubscribe?token=abc");
    context.put("appUrl", "http://localhost:3000");
    context.put("subject", subject);
    return context;
  }
}
