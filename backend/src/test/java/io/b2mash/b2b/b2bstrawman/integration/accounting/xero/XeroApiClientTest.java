package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link XeroApiClient}. Uses {@link MockRestServiceServer} to verify HTTP
 * interactions and rate-limit header parsing without a running server.
 */
class XeroApiClientTest {

  private static final XeroProperties TEST_PROPERTIES =
      new XeroProperties(
          "test-client-id",
          "test-client-secret",
          "http://localhost/callback",
          "https://login.xero.com/identity/connect/authorize",
          "https://identity.xero.com/connect/token",
          "https://api.xero.com/connections",
          "https://api.xero.com/api.xro/2.0",
          "https://identity.xero.com/connect/revocation");

  @Test
  void getTaxRates_sendsCorrectHeadersAndReturnsBody() {
    var builder = RestClient.builder().baseUrl(TEST_PROPERTIES.apiBaseUrl());
    var server = MockRestServiceServer.bindTo(builder).build();

    server
        .expect(MockRestRequestMatchers.requestTo(TEST_PROPERTIES.apiBaseUrl() + "/TaxRates"))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
        .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer test-access-token"))
        .andExpect(MockRestRequestMatchers.header("Xero-tenant-id", "tenant-123"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                    "{\"TaxRates\": [{\"Name\": \"GST\", \"TaxType\": \"OUTPUT\"}]}",
                    MediaType.APPLICATION_JSON)
                .headers(rateLimitHeaders(50)));

    var client = new XeroApiClient(builder.build(), TEST_PROPERTIES);
    var result = client.getTaxRates("tenant-123", "test-access-token");

    assertThat(result).containsKey("TaxRates");
    server.verify();
  }

  @Test
  void getContacts_withPagination_sendsCorrectUri() {
    var builder = RestClient.builder().baseUrl(TEST_PROPERTIES.apiBaseUrl());
    var server = MockRestServiceServer.bindTo(builder).build();

    server
        .expect(
            MockRestRequestMatchers.requestTo(TEST_PROPERTIES.apiBaseUrl() + "/Contacts?page=2"))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
        .andRespond(
            MockRestResponseCreators.withSuccess("{\"Contacts\": []}", MediaType.APPLICATION_JSON)
                .headers(rateLimitHeaders(30)));

    var client = new XeroApiClient(builder.build(), TEST_PROPERTIES);
    var result = client.getContacts("tenant-123", 2, "test-access-token");

    assertThat(result).containsKey("Contacts");
    server.verify();
  }

  @Test
  void createOrUpdateInvoice_postsPayloadWithCorrectHeaders() {
    var builder = RestClient.builder().baseUrl(TEST_PROPERTIES.apiBaseUrl());
    var server = MockRestServiceServer.bindTo(builder).build();

    server
        .expect(MockRestRequestMatchers.requestTo(TEST_PROPERTIES.apiBaseUrl() + "/Invoices"))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer access-tok"))
        .andExpect(MockRestRequestMatchers.header("Xero-tenant-id", "t-999"))
        .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                    "{\"Invoices\": [{\"InvoiceID\": \"inv-001\"}]}", MediaType.APPLICATION_JSON)
                .headers(rateLimitHeaders(40)));

    var client = new XeroApiClient(builder.build(), TEST_PROPERTIES);
    var payload = Map.<String, Object>of("Type", "ACCREC", "Contact", Map.of("Name", "Acme"));
    var result = client.createOrUpdateInvoice("t-999", payload, "access-tok");

    assertThat(result).containsKey("Invoices");
    server.verify();
  }

  @Test
  void throwsRateLimitException_whenRemainingBelowThreshold() {
    var builder = RestClient.builder().baseUrl(TEST_PROPERTIES.apiBaseUrl());
    var server = MockRestServiceServer.bindTo(builder).build();

    server
        .expect(MockRestRequestMatchers.requestTo(TEST_PROPERTIES.apiBaseUrl() + "/TaxRates"))
        .andRespond(
            MockRestResponseCreators.withSuccess("{\"TaxRates\": []}", MediaType.APPLICATION_JSON)
                .headers(rateLimitHeaders(3, 45)));

    var client = new XeroApiClient(builder.build(), TEST_PROPERTIES);

    assertThatThrownBy(() -> client.getTaxRates("tenant-123", "test-token"))
        .isInstanceOf(XeroRateLimitException.class)
        .satisfies(
            ex -> {
              var rle = (XeroRateLimitException) ex;
              assertThat(rle.getRetryAfter()).isEqualTo(Duration.ofSeconds(45));
            });

    server.verify();
  }

  @Test
  void doesNotThrowRateLimitException_whenRemainingAboveThreshold() {
    var builder = RestClient.builder().baseUrl(TEST_PROPERTIES.apiBaseUrl());
    var server = MockRestServiceServer.bindTo(builder).build();

    server
        .expect(MockRestRequestMatchers.requestTo(TEST_PROPERTIES.apiBaseUrl() + "/TaxRates"))
        .andRespond(
            MockRestResponseCreators.withSuccess("{\"TaxRates\": []}", MediaType.APPLICATION_JSON)
                .headers(rateLimitHeaders(10)));

    var client = new XeroApiClient(builder.build(), TEST_PROPERTIES);
    var result = client.getTaxRates("tenant-123", "test-token");

    assertThat(result).isNotNull();
    server.verify();
  }

  // ---- Helpers ----

  private static HttpHeaders rateLimitHeaders(int remaining) {
    return rateLimitHeaders(remaining, 60);
  }

  private static HttpHeaders rateLimitHeaders(int remaining, int retryAfter) {
    var headers = new HttpHeaders();
    headers.set("X-Rate-Limit-Remaining", String.valueOf(remaining));
    headers.set("Retry-After", String.valueOf(retryAfter));
    return headers;
  }
}
