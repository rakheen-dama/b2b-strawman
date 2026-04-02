package io.b2mash.b2b.b2bstrawman.billing.payfast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.billing.BillingProperties;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class PlatformPayFastServiceTest {

  private PayFastBillingProperties payfastProperties;
  private BillingProperties billingProperties;
  private RestClient restClient;
  private PlatformPayFastService service;

  @BeforeEach
  void setUp() throws Exception {
    payfastProperties = mock(PayFastBillingProperties.class);
    billingProperties = mock(BillingProperties.class);
    restClient = mock(RestClient.class);

    service = new PlatformPayFastService(payfastProperties, billingProperties);
    setField(service, "restClient", restClient);
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  /** Stubs all properties needed for checkout form generation. */
  private void stubFormGenerationProperties() {
    when(payfastProperties.merchantId()).thenReturn("10000100");
    when(payfastProperties.merchantKey()).thenReturn("46f0cd694581a");
    when(payfastProperties.passphrase()).thenReturn("jt7NOE43FZPn");
    when(payfastProperties.sandbox()).thenReturn(true);
    when(billingProperties.returnUrl()).thenReturn("https://example.com/return");
    when(billingProperties.cancelUrl()).thenReturn("https://example.com/cancel");
    when(billingProperties.notifyUrl()).thenReturn("https://example.com/notify");
    when(billingProperties.monthlyPriceCents()).thenReturn(49900);
    when(billingProperties.itemName()).thenReturn("HeyKazi Professional");
  }

  @Test
  void generateCheckoutForm_containsAllRequiredPayFastFields() {
    stubFormGenerationProperties();
    var orgId = UUID.randomUUID();

    var response = service.generateCheckoutForm(orgId);

    assertThat(response.formFields())
        .containsKeys(
            "merchant_id",
            "merchant_key",
            "return_url",
            "cancel_url",
            "notify_url",
            "amount",
            "item_name",
            "subscription_type",
            "recurring_amount",
            "frequency",
            "cycles",
            "custom_str1",
            "signature");
    assertThat(response.formFields()).hasSize(13);
  }

  @Test
  void generateCheckoutForm_customStr1ContainsOrganizationId() {
    stubFormGenerationProperties();
    var orgId = UUID.randomUUID();

    var response = service.generateCheckoutForm(orgId);

    assertThat(response.formFields().get("custom_str1")).isEqualTo(orgId.toString());
  }

  @Test
  void generateCheckoutForm_signatureIsValidMd5() {
    stubFormGenerationProperties();
    var orgId = UUID.randomUUID();

    var response = service.generateCheckoutForm(orgId);

    String signature = response.formFields().get("signature");
    assertThat(signature).matches("[a-f0-9]{32}");
  }

  @Test
  void generateCheckoutForm_usesSandboxUrlWhenSandboxTrue() {
    stubFormGenerationProperties();
    when(payfastProperties.sandbox()).thenReturn(true);

    var response = service.generateCheckoutForm(UUID.randomUUID());

    assertThat(response.paymentUrl()).startsWith("https://sandbox.payfast.co.za");
  }

  @Test
  void generateCheckoutForm_usesProductionUrlWhenSandboxFalse() {
    stubFormGenerationProperties();
    when(payfastProperties.sandbox()).thenReturn(false);

    var response = service.generateCheckoutForm(UUID.randomUUID());

    assertThat(response.paymentUrl()).startsWith("https://www.payfast.co.za");
  }

  @Test
  void formatCentsToRands_converts49900To499_00() {
    assertThat(service.formatCentsToRands(49900)).isEqualTo("499.00");
  }

  @Test
  void formatCentsToRands_converts100To1_00() {
    assertThat(service.formatCentsToRands(100)).isEqualTo("1.00");
  }

  @Test
  void cancelPayFastSubscription_sendsCorrectUrlForSandbox() {
    when(payfastProperties.sandbox()).thenReturn(true);
    when(payfastProperties.merchantId()).thenReturn("10000100");
    when(payfastProperties.passphrase()).thenReturn("jt7NOE43FZPn");

    var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    var requestBodySpec = mock(RestClient.RequestBodySpec.class);
    var responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.put()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString()))
        .thenAnswer(
            invocation -> {
              String uri = invocation.getArgument(0);
              assertThat(uri).contains("sandbox.payfast.co.za");
              assertThat(uri).contains("test-token-123");
              return requestBodySpec;
            });
    when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(null);

    service.cancelPayFastSubscription("test-token-123");
  }

  @Test
  void cancelPayFastSubscription_sendsCorrectUrlForProduction() {
    when(payfastProperties.sandbox()).thenReturn(false);
    when(payfastProperties.merchantId()).thenReturn("10000100");
    when(payfastProperties.passphrase()).thenReturn("jt7NOE43FZPn");

    var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    var requestBodySpec = mock(RestClient.RequestBodySpec.class);
    var responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.put()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString()))
        .thenAnswer(
            invocation -> {
              String uri = invocation.getArgument(0);
              assertThat(uri).contains("api.payfast.co.za");
              assertThat(uri).contains("prod-token-456");
              return requestBodySpec;
            });
    when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(null);

    service.cancelPayFastSubscription("prod-token-456");
  }

  @Test
  void cancelPayFastSubscription_throwsInvalidStateExceptionOnError() {
    when(payfastProperties.sandbox()).thenReturn(true);
    when(payfastProperties.merchantId()).thenReturn("10000100");
    when(payfastProperties.passphrase()).thenReturn("jt7NOE43FZPn");

    var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    var requestBodySpec = mock(RestClient.RequestBodySpec.class);

    when(restClient.put()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenThrow(new RuntimeException("Connection refused"));

    assertThatThrownBy(() -> service.cancelPayFastSubscription("bad-token"))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void generateCheckoutForm_signatureMatchesExpectedGoldenValue() {
    stubFormGenerationProperties();
    var orgId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    var response = service.generateCheckoutForm(orgId);

    // Pre-computed MD5 of the alphabetically-sorted, URL-encoded param string
    // with passphrase "jt7NOE43FZPn" appended
    assertThat(response.formFields().get("signature"))
        .isEqualTo("df17f1d2b11fe8f7d73f10c34ce72eae");
  }
}
