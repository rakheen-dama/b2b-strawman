package io.b2mash.b2b.b2bstrawman.demo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.integration.email.RenderedEmail;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Plain unit test for the fire-and-forget contract of {@link DemoWelcomeEmailService}: neither an
 * absent mail sender nor a failing send may ever propagate an exception.
 */
class DemoWelcomeEmailServiceTest {

  @Test
  void sendWelcomeEmail_noMailSenderConfigured_doesNotThrow() {
    var welcomeEmailService =
        new DemoWelcomeEmailService(
            Optional.empty(), mock(EmailTemplateRenderer.class), "noreply@kazi.app", "Kazi");

    assertDoesNotThrow(
        () ->
            welcomeEmailService.sendWelcomeEmail(
                "test@example.com",
                "Test Org",
                "test-org",
                "legal-za",
                "http://localhost:3000/org/test-org",
                "TempPass123!"));
  }

  @Test
  void sendWelcomeEmail_sendThrows_doesNotThrow() {
    var renderer = mock(EmailTemplateRenderer.class);
    when(renderer.render(eq("demo-welcome"), anyMap()))
        .thenReturn(new RenderedEmail("Welcome to Kazi", "<p>Welcome</p>", "Welcome"));

    var mailSender = mock(JavaMailSender.class);
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    doThrow(new MailSendException("SMTP unavailable"))
        .when(mailSender)
        .send(any(MimeMessage.class));

    var welcomeEmailService =
        new DemoWelcomeEmailService(Optional.of(mailSender), renderer, "noreply@kazi.app", "Kazi");

    assertDoesNotThrow(
        () ->
            welcomeEmailService.sendWelcomeEmail(
                "test@example.com",
                "Test Org",
                "test-org",
                "legal-za",
                "http://localhost:3000/org/test-org",
                "TempPass123!"));

    // Proves the failing send was actually reached (fire-and-forget catch swallowed it).
    verify(mailSender).send(any(MimeMessage.class));
  }
}
