package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NoOpEmailProviderTest {

  private final NoOpEmailProvider provider = new NoOpEmailProvider();

  @Test
  void sendEmail_returns_success_with_noop_id() {
    var message =
        new EmailMessage(
            "recipient@example.com", "Test Subject", "<h1>Hello</h1>", "Hello", null, Map.of());

    var result = provider.sendEmail(message);

    assertThat(result.success()).isTrue();
    assertThat(result.providerMessageId()).startsWith("NOOP-");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void sendEmailWithAttachment_returns_success() {
    var message =
        new EmailMessage(
            "recipient@example.com",
            "Invoice",
            "<p>See attached</p>",
            "See attached",
            null,
            Map.of());
    var attachment = new EmailAttachment("invoice.pdf", "application/pdf", "content".getBytes());

    var result = provider.sendEmailWithAttachment(message, attachment);

    assertThat(result.success()).isTrue();
    assertThat(result.providerMessageId()).startsWith("NOOP-");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testConnection_returns_success() {
    var result = provider.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("noop");
    assertThat(result.errorMessage()).isNull();
  }
}
