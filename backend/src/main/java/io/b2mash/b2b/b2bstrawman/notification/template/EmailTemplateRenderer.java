package io.b2mash.b2b.b2bstrawman.notification.template;

import io.b2mash.b2b.b2bstrawman.integration.email.RenderedEmail;
import io.b2mash.b2b.b2bstrawman.template.LenientStandardDialect;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders branded HTML emails from Thymeleaf classpath templates. Uses a
 * ClassLoaderTemplateResolver pointing to templates/email/ with the LenientStandardDialect from
 * Phase 12 to gracefully handle missing context variables.
 *
 * <p>Rendering uses a two-pass approach: first the content template is rendered, then the result is
 * injected into the base layout template as unescaped HTML.
 */
@Service
public class EmailTemplateRenderer {

  private static final Logger log = LoggerFactory.getLogger(EmailTemplateRenderer.class);

  private final TemplateEngine emailTemplateEngine;

  public EmailTemplateRenderer() {
    this.emailTemplateEngine = createEmailTemplateEngine();
  }

  /**
   * Renders an email template with the given context using two-pass rendering.
   *
   * <p>Pass 1: Renders the content template (e.g., "test-email") with the provided context. Pass 2:
   * Injects the rendered content HTML into the base layout template.
   *
   * @param templateName template name without path or suffix (e.g., "test-email")
   * @param context template variables
   * @return RenderedEmail with subject extracted from context, HTML body, and plain-text fallback
   */
  public RenderedEmail render(String templateName, Map<String, Object> context) {
    var ctx = new Context();
    context.forEach(ctx::setVariable);

    // Pass 1: Render the content template
    String contentHtml = emailTemplateEngine.process(templateName, ctx);

    // Pass 2: Inject rendered content into base layout
    ctx.setVariable("contentHtml", contentHtml);
    String fullHtml = emailTemplateEngine.process("base", ctx);

    String plainTextBody = toPlainText(fullHtml);
    String subject = context.containsKey("subject") ? (String) context.get("subject") : "DocTeams";

    log.debug("Rendered email template '{}', HTML size={}", templateName, fullHtml.length());

    return new RenderedEmail(subject, fullHtml, plainTextBody);
  }

  /**
   * Strips HTML tags to produce a plain-text fallback body. Preserves link text with URL in
   * parentheses. Collapses whitespace.
   */
  String toPlainText(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }

    String text = html;

    // Preserve link text with URL: <a href="url">text</a> -> text (url)
    text = text.replaceAll("<a[^>]*href=\"([^\"]*)\"[^>]*>([^<]*)</a>", "$2 ($1)");

    // Replace <br>, <br/>, <p>, <div> with newlines
    text = text.replaceAll("<br\\s*/?>", "\n");
    text = text.replaceAll("</p>", "\n\n");
    text = text.replaceAll("</div>", "\n");
    text = text.replaceAll("</tr>", "\n");
    text = text.replaceAll("</td>", " ");

    // Strip all remaining HTML tags
    text = text.replaceAll("<[^>]+>", "");

    // Decode common HTML entities
    text = text.replace("&amp;", "&");
    text = text.replace("&lt;", "<");
    text = text.replace("&gt;", ">");
    text = text.replace("&quot;", "\"");
    text = text.replace("&nbsp;", " ");
    text = text.replace("&#39;", "'");

    // Collapse multiple whitespace (but keep newlines)
    text = text.replaceAll("[ \\t]+", " ");
    // Collapse multiple blank lines into two newlines
    text = text.replaceAll("\\n{3,}", "\n\n");

    return text.strip();
  }

  private static TemplateEngine createEmailTemplateEngine() {
    var engine = new TemplateEngine();
    engine.setDialect(new LenientStandardDialect());

    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/email/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    resolver.setCacheable(true);

    engine.setTemplateResolver(resolver);
    return engine;
  }
}
