package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Thin HTTP wrapper for the Xero API. Each method takes an {@code accessToken} parameter — the
 * caller manages token lifecycle. Reads rate-limit headers on every response and throws {@link
 * XeroRateLimitException} when {@code X-Rate-Limit-Remaining < 5}.
 *
 * <p>This client does NOT handle 401 retry — per architecture, the calling layer is responsible for
 * catching 401 and refreshing tokens via {@link XeroOAuthService}.
 */
@Service
public class XeroApiClient {

  private static final Logger log = LoggerFactory.getLogger(XeroApiClient.class);
  private static final int RATE_LIMIT_THRESHOLD = 5;
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final XeroProperties properties;

  @Autowired
  public XeroApiClient(XeroProperties properties) {
    this.properties = properties;

    var httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(30));

    this.restClient =
        RestClient.builder()
            .baseUrl(properties.apiBaseUrl())
            .requestFactory(requestFactory)
            .build();
  }

  /** Package-private constructor for testing with a pre-built RestClient. */
  XeroApiClient(RestClient restClient, XeroProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  /** POST or PUT an invoice to Xero. */
  public Map<String, Object> createOrUpdateInvoice(
      String xeroTenantId, Map<String, Object> invoicePayload, String accessToken) {
    return postWithTenantId("/Invoices", xeroTenantId, invoicePayload, accessToken);
  }

  /** POST or PUT a contact to Xero. */
  public Map<String, Object> createOrUpdateContact(
      String xeroTenantId, Map<String, Object> contactPayload, String accessToken) {
    return postWithTenantId("/Contacts", xeroTenantId, contactPayload, accessToken);
  }

  /** GET invoices modified since the given timestamp with PAID status. */
  public Map<String, Object> getInvoicesModifiedSince(
      String xeroTenantId, Instant since, String accessToken) {
    String where = "UpdatedDateUTC>DateTime(" + formatXeroDateTime(since) + ")";
    return getWithTenantId(
        "/Invoices?where={where}&Statuses=PAID", xeroTenantId, accessToken, where);
  }

  /** GET contacts with pagination. */
  public Map<String, Object> getContacts(String xeroTenantId, int page, String accessToken) {
    return getWithTenantId("/Contacts?page={page}", xeroTenantId, accessToken, page);
  }

  /** GET tax rates for the connected Xero org. */
  public Map<String, Object> getTaxRates(String xeroTenantId, String accessToken) {
    return getWithTenantId("/TaxRates", xeroTenantId, accessToken);
  }

  /**
   * GET the list of connected Xero tenants. Uses the connections endpoint (not the API base URL)
   * and does NOT send the Xero-tenant-id header.
   */
  public List<Map<String, Object>> getConnections(String accessToken) {
    var response =
        restClient
            .get()
            .uri(properties.connectionsUrl())
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (req, resp) -> {
                  throw new XeroApiException(
                      "Xero connections request failed: HTTP " + resp.getStatusCode().value());
                })
            .toEntity(LIST_MAP_TYPE);

    return response.getBody() != null ? response.getBody() : List.of();
  }

  // ---- Private helpers ----

  private Map<String, Object> getWithTenantId(
      String uriTemplate, String xeroTenantId, String accessToken, Object... uriVars) {
    var response =
        restClient
            .get()
            .uri(uriTemplate, uriVars)
            .header("Authorization", "Bearer " + accessToken)
            .header("Xero-tenant-id", xeroTenantId)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (req, resp) -> {
                  throw new XeroApiException(
                      "Xero API error: HTTP " + resp.getStatusCode().value());
                })
            .toEntity(MAP_TYPE);

    checkRateLimit(response);
    return response.getBody() != null ? response.getBody() : Map.of();
  }

  private Map<String, Object> postWithTenantId(
      String path, String xeroTenantId, Object body, String accessToken) {
    var response =
        restClient
            .post()
            .uri(path)
            .header("Authorization", "Bearer " + accessToken)
            .header("Xero-tenant-id", xeroTenantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (req, resp) -> {
                  throw new XeroApiException(
                      "Xero API error: HTTP " + resp.getStatusCode().value());
                })
            .toEntity(MAP_TYPE);

    checkRateLimit(response);
    return response.getBody() != null ? response.getBody() : Map.of();
  }

  private void checkRateLimit(ResponseEntity<?> response) {
    var headers = response.getHeaders();
    String remainingHeader = headers.getFirst("X-Rate-Limit-Remaining");
    String retryAfterHeader = headers.getFirst("Retry-After");

    if (remainingHeader != null) {
      try {
        int remaining = Integer.parseInt(remainingHeader);
        if (remaining < RATE_LIMIT_THRESHOLD) {
          Duration retryAfter =
              retryAfterHeader != null
                  ? Duration.ofSeconds(Long.parseLong(retryAfterHeader))
                  : Duration.ofSeconds(60);
          log.warn(
              "Xero rate limit approaching: {} calls remaining, retry after {}s",
              remaining,
              retryAfter.toSeconds());
          throw new XeroRateLimitException(
              "Xero rate limit approaching: " + remaining + " calls remaining", retryAfter);
        }
      } catch (NumberFormatException e) {
        log.debug("Could not parse X-Rate-Limit-Remaining header: {}", remainingHeader);
      }
    }
  }

  private String formatXeroDateTime(Instant instant) {
    var zdt = instant.atZone(java.time.ZoneOffset.UTC);
    return String.format(
        "%d,%d,%d,%d,%d,%d",
        zdt.getYear(),
        zdt.getMonthValue(),
        zdt.getDayOfMonth(),
        zdt.getHour(),
        zdt.getMinute(),
        zdt.getSecond());
  }

  /** Internal exception for non-rate-limit Xero API errors. */
  static class XeroApiException extends RuntimeException {
    XeroApiException(String message) {
      super(message);
    }
  }
}
