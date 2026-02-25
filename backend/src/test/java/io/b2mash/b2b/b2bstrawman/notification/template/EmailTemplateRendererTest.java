package io.b2mash.b2b.b2bstrawman.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.integration.email.RenderedEmail;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {

  private EmailTemplateRenderer renderer;

  @BeforeEach
  void setUp() {
    renderer = new EmailTemplateRenderer();
  }

  @Test
  void render_test_email_contains_org_name() {
    var context = buildContext();
    context.put("orgName", "Acme Corp");
    context.put("subject", "Test Email");

    RenderedEmail result = renderer.render("test-email", context);

    assertThat(result.htmlBody()).contains("Acme Corp");
    assertThat(result.subject()).isEqualTo("Test Email");
  }

  @Test
  void render_with_brand_color_applies_to_header() {
    var context = buildContext();
    context.put("brandColor", "#FF5733");

    RenderedEmail result = renderer.render("test-email", context);

    assertThat(result.htmlBody()).contains("#FF5733");
  }

  @Test
  void render_missing_variable_does_not_throw() {
    // Minimal context — many variables missing
    var context = new HashMap<String, Object>();
    context.put("subject", "Test");

    RenderedEmail result = renderer.render("test-email", context);

    assertThat(result.htmlBody()).isNotEmpty();
    assertThat(result.subject()).isEqualTo("Test");
  }

  @Test
  void toPlainText_strips_html_tags() {
    String html = "<p>Hello <strong>world</strong></p>";
    String plain = renderer.toPlainText(html);

    assertThat(plain).contains("Hello");
    assertThat(plain).contains("world");
    assertThat(plain).doesNotContain("<p>");
    assertThat(plain).doesNotContain("<strong>");
  }

  @Test
  void toPlainText_preserves_link_text() {
    String html = "<a href=\"https://example.com\">Click here</a>";
    String plain = renderer.toPlainText(html);

    assertThat(plain).contains("Click here");
    assertThat(plain).contains("https://example.com");
  }

  @Test
  void render_base_layout_includes_footer() {
    var context = buildContext();
    context.put("footerText", "Copyright 2026 Acme");

    RenderedEmail result = renderer.render("test-email", context);

    assertThat(result.htmlBody()).contains("Copyright 2026 Acme");
  }

  private Map<String, Object> buildContext() {
    var context = new HashMap<String, Object>();
    context.put("orgName", "Test Org");
    context.put("orgLogoUrl", null);
    context.put("brandColor", "#2563EB");
    context.put("footerText", null);
    context.put("recipientName", "Alice");
    context.put("unsubscribeUrl", null);
    context.put("appUrl", "http://localhost:3000");
    context.put("subject", "DocTeams");
    return context;
  }
}
