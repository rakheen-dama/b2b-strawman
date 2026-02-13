package io.b2mash.b2b.b2bstrawman.notification.template;

import org.springframework.stereotype.Component;

/**
 * Renders email content from templates and notification data. In Phase 6.5, this is a thin wrapper
 * around EmailTemplate. Future phases can inject Thymeleaf TemplateEngine for HTML rendering.
 */
@Component
public class TemplateRenderer {

  // Placeholder for future Thymeleaf integration.
  // Phase 6.5 uses EmailTemplate enum directly.
}
