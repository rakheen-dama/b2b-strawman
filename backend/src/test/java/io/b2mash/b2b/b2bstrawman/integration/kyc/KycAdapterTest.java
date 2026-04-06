package io.b2mash.b2b.b2bstrawman.integration.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import org.junit.jupiter.api.Test;

/** Unit tests for KYC adapter implementations. */
class KycAdapterTest {

  private final SecretStore secretStore = mock(SecretStore.class);

  // --- VerifyNowKycAdapter ---

  @Test
  void verifyNow_returnsNeedsReviewWithProviderReference() {
    when(secretStore.retrieve(anyString())).thenReturn("test-api-key");
    var adapter = new VerifyNowKycAdapter(secretStore);
    var request = new KycVerificationRequest("9001015009087", "John Doe", "1990-01-01", "SA_ID");

    var result = adapter.verify(request);

    assertThat(result.status()).isEqualTo(KycVerificationStatus.NEEDS_REVIEW);
    assertThat(result.providerName()).isEqualTo("verifynow");
    assertThat(result.providerReference()).startsWith("VN-");
    assertThat(result.metadata()).containsEntry("api_key_configured", "true");
  }

  @Test
  void verifyNow_returnsErrorWhenApiKeyMissing() {
    when(secretStore.retrieve(anyString()))
        .thenThrow(new ResourceNotFoundException("Secret", "kyc_verification:verifynow:api_key"));
    var adapter = new VerifyNowKycAdapter(secretStore);
    var request = new KycVerificationRequest("9001015009087", "John Doe", null, "SA_ID");

    var result = adapter.verify(request);

    assertThat(result.status()).isEqualTo(KycVerificationStatus.ERROR);
    assertThat(result.providerName()).isEqualTo("verifynow");
    assertThat(result.reasonCode()).isEqualTo("PROVIDER_ERROR");
  }

  @Test
  void verifyNow_testConnectionSucceedsWithApiKey() {
    when(secretStore.retrieve(anyString())).thenReturn("test-api-key");
    var adapter = new VerifyNowKycAdapter(secretStore);

    var result = adapter.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("verifynow");
  }

  // --- CheckIdKycAdapter ---

  @Test
  void checkId_alwaysReturnsNeedsReview() {
    when(secretStore.retrieve(anyString())).thenReturn("test-api-key");
    var adapter = new CheckIdKycAdapter(secretStore);
    var request = new KycVerificationRequest("9001015009087", "Jane Smith", "1990-01-01", "SA_ID");

    var result = adapter.verify(request);

    assertThat(result.status()).isEqualTo(KycVerificationStatus.NEEDS_REVIEW);
    assertThat(result.providerName()).isEqualTo("checkid");
    assertThat(result.providerReference()).startsWith("CID-");
    assertThat(result.reasonCode()).isEqualTo("FORMAT_VALIDATED");
  }

  @Test
  void checkId_extractsBirthDateAndCitizenshipFromIdNumber() {
    when(secretStore.retrieve(anyString())).thenReturn("test-api-key");
    var adapter = new CheckIdKycAdapter(secretStore);
    var request = new KycVerificationRequest("9001015009087", "Jane Smith", null, "SA_ID");

    var result = adapter.verify(request);

    assertThat(result.metadata()).containsEntry("extracted_birth_date", "1990-01-01");
    assertThat(result.metadata()).containsEntry("extracted_citizenship", "SA_CITIZEN");
    assertThat(result.metadata()).containsEntry("format_valid", "true");
  }

  @Test
  void checkId_extractsYear2000sBirthDateCorrectly() {
    when(secretStore.retrieve(anyString())).thenReturn("test-api-key");
    var adapter = new CheckIdKycAdapter(secretStore);
    // ID starting with 0501... => born 2005-01-15 (YY=05 <= 25 => 2000s)
    var request = new KycVerificationRequest("0501155009087", "Young Person", null, "SA_ID");

    var result = adapter.verify(request);

    assertThat(result.metadata()).containsEntry("extracted_birth_date", "2005-01-15");
    assertThat(result.metadata()).containsEntry("format_valid", "true");
  }

  @Test
  void checkId_marksFormatInvalidForShortIdNumber() {
    when(secretStore.retrieve(anyString())).thenReturn("test-api-key");
    var adapter = new CheckIdKycAdapter(secretStore);
    var request = new KycVerificationRequest("123", "Jane Smith", null, "SA_ID");

    var result = adapter.verify(request);

    assertThat(result.status()).isEqualTo(KycVerificationStatus.NEEDS_REVIEW);
    assertThat(result.metadata()).containsEntry("format_valid", "false");
  }

  // --- NoOpKycAdapter ---

  @Test
  void noOp_returnsError() {
    var adapter = new NoOpKycAdapter();
    var request = new KycVerificationRequest("9001015009087", "John Doe", null, "SA_ID");

    var result = adapter.verify(request);

    assertThat(result.status()).isEqualTo(KycVerificationStatus.ERROR);
    assertThat(result.providerName()).isEqualTo("noop");
    assertThat(result.reasonCode()).isEqualTo("NO_PROVIDER");
    assertThat(result.reasonDescription()).isEqualTo("No KYC provider configured");
  }

  @Test
  void noOp_testConnectionSucceeds() {
    var adapter = new NoOpKycAdapter();

    var result = adapter.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("noop");
  }
}
