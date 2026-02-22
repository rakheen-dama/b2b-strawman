package io.b2mash.b2b.b2bstrawman.integration.signing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoOpSigningProviderTest {

  private final NoOpSigningProvider provider = new NoOpSigningProvider();

  @Test
  void providerId_returns_noop() {
    assertThat(provider.providerId()).isEqualTo("noop");
  }

  @Test
  void sendForSignature_returns_success_with_noop_sign_reference() {
    var request =
        new SigningRequest(
            new byte[] {1, 2, 3}, "application/pdf", "Jane Doe", "jane@example.com", null);

    var result = provider.sendForSignature(request);

    assertThat(result.success()).isTrue();
    assertThat(result.signingReference()).startsWith("NOOP-SIGN-");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void checkStatus_returns_signed_state() {
    var status = provider.checkStatus("REF-123");

    assertThat(status.state()).isEqualTo(SigningState.SIGNED);
    assertThat(status.signingReference()).isEqualTo("REF-123");
    assertThat(status.updatedAt()).isNotNull();
  }

  @Test
  void downloadSigned_returns_empty_bytes() {
    var bytes = provider.downloadSigned("REF-123");

    assertThat(bytes).isEmpty();
  }

  @Test
  void testConnection_returns_success() {
    var result = provider.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("noop");
    assertThat(result.errorMessage()).isNull();
  }
}
