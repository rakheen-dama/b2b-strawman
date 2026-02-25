package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailProviderIntegrationTest {

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

  private SmtpEmailProvider provider;

  @BeforeEach
  void setUp() {
    var mailSender = new JavaMailSenderImpl();
    mailSender.setHost(greenMail.getSmtp().getBindTo());
    mailSender.setPort(greenMail.getSmtp().getPort());
    provider = new SmtpEmailProvider(mailSender, "test@docteams.app");
  }

  @Test
  void sendEmail_delivers_to_greenmail() throws Exception {
    var message =
        new EmailMessage(
            "recipient@example.com", "Test Subject", "<h1>Hello</h1>", "Hello", null, Map.of());

    var result = provider.sendEmail(message);

    assertThat(result.success()).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getSubject()).isEqualTo("Test Subject");
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("recipient@example.com");
  }

  @Test
  void sendEmail_returns_message_id() {
    var message =
        new EmailMessage(
            "recipient@example.com", "Test Subject", "<h1>Hello</h1>", "Hello", null, Map.of());

    var result = provider.sendEmail(message);

    assertThat(result.success()).isTrue();
    assertThat(result.providerMessageId()).isNotNull().isNotEmpty();
  }

  @Test
  void sendEmailWithAttachment_includes_pdf() throws Exception {
    var message =
        new EmailMessage(
            "recipient@example.com",
            "Invoice Attached",
            "<p>See attached</p>",
            "See attached",
            null,
            Map.of());
    var attachment =
        new EmailAttachment("invoice.pdf", "application/pdf", "PDF content".getBytes());

    var result = provider.sendEmailWithAttachment(message, attachment);

    assertThat(result.success()).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    MimeMultipart multipart = (MimeMultipart) received[0].getContent();
    // Multipart message: text parts + attachment
    boolean hasAttachment = false;
    for (int i = 0; i < multipart.getCount(); i++) {
      var part = multipart.getBodyPart(i);
      if ("invoice.pdf".equals(part.getFileName())) {
        hasAttachment = true;
      }
    }
    assertThat(hasAttachment).isTrue();
  }

  @Test
  void sendEmail_failure_returns_error_result() {
    // Create a provider pointing to an unreachable host
    var badMailSender = new JavaMailSenderImpl();
    badMailSender.setHost("unreachable.invalid");
    badMailSender.setPort(9999);
    var badProvider = new SmtpEmailProvider(badMailSender, "test@docteams.app");

    var message =
        new EmailMessage("recipient@example.com", "Test", "<p>test</p>", "test", null, Map.of());

    var result = badProvider.sendEmail(message);

    assertThat(result.success()).isFalse();
    assertThat(result.providerMessageId()).isNull();
    assertThat(result.errorMessage()).isNotNull();
  }

  @Test
  void testConnection_succeeds_with_greenmail() {
    var result = provider.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("smtp");
    assertThat(result.errorMessage()).isNull();
  }
}
