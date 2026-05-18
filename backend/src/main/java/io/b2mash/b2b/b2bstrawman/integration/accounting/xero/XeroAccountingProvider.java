package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingPaymentSource;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingSyncResult;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMapping;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMappingService;
import io.b2mash.b2b.b2bstrawman.integration.accounting.CustomerSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.ExternalPaymentEvent;
import io.b2mash.b2b.b2bstrawman.integration.accounting.InvoiceSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Xero-specific implementation of {@link AccountingProvider} and {@link AccountingPaymentSource}.
 * Translates Kazi domain objects into Xero API payloads and Xero responses back into Kazi result
 * types. Registered via {@link IntegrationAdapter} for auto-discovery by the IntegrationRegistry.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.ACCOUNTING, slug = "xero")
public class XeroAccountingProvider implements AccountingProvider, AccountingPaymentSource {

  private static final Logger log = LoggerFactory.getLogger(XeroAccountingProvider.class);

  private final XeroApiClient xeroApiClient;
  private final XeroOAuthService xeroOAuthService;
  private final XeroInvoicePayloadMapper invoicePayloadMapper;
  private final AccountingXeroConnectionRepository connectionRepository;
  private final AccountingTaxCodeMappingService taxCodeMappingService;
  private final SecretStore secretStore;

  public XeroAccountingProvider(
      XeroApiClient xeroApiClient,
      XeroOAuthService xeroOAuthService,
      XeroInvoicePayloadMapper invoicePayloadMapper,
      AccountingXeroConnectionRepository connectionRepository,
      AccountingTaxCodeMappingService taxCodeMappingService,
      SecretStore secretStore) {
    this.xeroApiClient = xeroApiClient;
    this.xeroOAuthService = xeroOAuthService;
    this.invoicePayloadMapper = invoicePayloadMapper;
    this.connectionRepository = connectionRepository;
    this.taxCodeMappingService = taxCodeMappingService;
    this.secretStore = secretStore;
  }

  @Override
  public String providerId() {
    return "xero";
  }

  @Override
  public AccountingSyncResult syncInvoice(InvoiceSyncRequest request) {
    try {
      var connection = loadConnectedConnection();
      String accessToken = getAccessToken(connection);
      List<AccountingTaxCodeMapping> taxMappings = taxCodeMappingService.getByProvider("xero");

      Map<String, Object> payload = invoicePayloadMapper.map(request, taxMappings);
      Map<String, Object> response =
          xeroApiClient.createOrUpdateInvoice(connection.getXeroTenantId(), payload, accessToken);

      String externalId = extractInvoiceId(response);
      log.info(
          "Xero invoice sync success: invoice={}, externalId={}",
          request.invoiceNumber(),
          externalId);
      return new AccountingSyncResult(true, externalId, null);

    } catch (XeroApiClient.XeroApiException e) {
      log.warn("Xero invoice sync failed for {}: {}", request.invoiceNumber(), e.getMessage());
      return new AccountingSyncResult(false, null, e.getMessage());
    }
  }

  @Override
  public AccountingSyncResult syncCustomer(CustomerSyncRequest request) {
    try {
      var connection = loadConnectedConnection();
      String accessToken = getAccessToken(connection);

      Map<String, Object> contactPayload = buildContactPayload(request);
      Map<String, Object> response =
          xeroApiClient.createOrUpdateContact(
              connection.getXeroTenantId(), contactPayload, accessToken);

      String externalId = extractContactId(response);
      log.info(
          "Xero customer sync success: customer={}, externalId={}",
          request.customerName(),
          externalId);
      return new AccountingSyncResult(true, externalId, null);

    } catch (XeroApiClient.XeroApiException e) {
      log.warn("Xero customer sync failed for {}: {}", request.customerName(), e.getMessage());
      return new AccountingSyncResult(false, null, e.getMessage());
    }
  }

  @Override
  public List<ExternalPaymentEvent> getPaymentsModifiedSince(Instant since) {
    var connection = loadConnectedConnection();
    String accessToken = getAccessToken(connection);

    Map<String, Object> response =
        xeroApiClient.getInvoicesModifiedSince(connection.getXeroTenantId(), since, accessToken);

    return mapPaymentResponse(response);
  }

  @Override
  public ConnectionTestResult testConnection() {
    try {
      var connection = loadConnectedConnection();
      String accessToken = getAccessToken(connection);

      var connections = xeroApiClient.getConnections(accessToken);
      if (connections.isEmpty()) {
        return new ConnectionTestResult(false, "xero", "No Xero tenants found");
      }
      return new ConnectionTestResult(true, "xero", null);

    } catch (Exception e) {
      log.warn("Xero connection test failed: {}", e.getMessage());
      return new ConnectionTestResult(false, "xero", e.getMessage());
    }
  }

  // ---- Private helpers ----

  private AccountingXeroConnection loadConnectedConnection() {
    return connectionRepository.findByStatus(XeroConnectionStatus.CONNECTED).stream()
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException("No active Xero connection found with CONNECTED status"));
  }

  private String getAccessToken(AccountingXeroConnection connection) {
    String secretKey = connection.getOrgIntegrationId().toString() + ":xero:access";
    return secretStore.retrieve(secretKey);
  }

  /**
   * Builds a simple Xero contact payload from a CustomerSyncRequest. A dedicated
   * XeroContactPayloadMapper lands in 520B.
   */
  private Map<String, Object> buildContactPayload(CustomerSyncRequest request) {
    var contact = new HashMap<String, Object>();
    contact.put("Name", request.customerName());

    if (request.email() != null) {
      contact.put("EmailAddress", request.email());
    }

    // Build address if any address fields are present
    if (request.addressLine1() != null || request.city() != null || request.postalCode() != null) {
      var address = new HashMap<String, Object>();
      address.put("AddressType", "POBOX");
      if (request.addressLine1() != null) {
        address.put("AddressLine1", request.addressLine1());
      }
      if (request.addressLine2() != null) {
        address.put("AddressLine2", request.addressLine2());
      }
      if (request.city() != null) {
        address.put("City", request.city());
      }
      if (request.postalCode() != null) {
        address.put("PostalCode", request.postalCode());
      }
      if (request.country() != null) {
        address.put("Country", request.country());
      }
      contact.put("Addresses", List.of(address));
    }

    return contact;
  }

  /** Extracts the InvoiceID from the Xero API response. */
  @SuppressWarnings("unchecked")
  private String extractInvoiceId(Map<String, Object> response) {
    var invoices = (List<Map<String, Object>>) response.get("Invoices");
    if (invoices != null && !invoices.isEmpty()) {
      Object invoiceId = invoices.getFirst().get("InvoiceID");
      if (invoiceId != null) {
        return invoiceId.toString();
      }
    }
    return null;
  }

  /** Extracts the ContactID from the Xero API response. */
  @SuppressWarnings("unchecked")
  private String extractContactId(Map<String, Object> response) {
    var contacts = (List<Map<String, Object>>) response.get("Contacts");
    if (contacts != null && !contacts.isEmpty()) {
      Object contactId = contacts.getFirst().get("ContactID");
      if (contactId != null) {
        return contactId.toString();
      }
    }
    return null;
  }

  /**
   * Maps a Xero paid-invoice response to a list of {@link ExternalPaymentEvent} records. Extracts
   * Reference (Kazi's external reference), payment details, and currency from each paid invoice.
   */
  @SuppressWarnings("unchecked")
  static List<ExternalPaymentEvent> mapPaymentResponse(Map<String, Object> response) {
    var invoices = (List<Map<String, Object>>) response.get("Invoices");
    if (invoices == null || invoices.isEmpty()) {
      return List.of();
    }

    var events = new ArrayList<ExternalPaymentEvent>();
    for (Map<String, Object> invoice : invoices) {
      String reference = (String) invoice.get("Reference");
      String currencyCode = (String) invoice.get("CurrencyCode");

      var payments = (List<Map<String, Object>>) invoice.get("Payments");
      if (payments == null || payments.isEmpty()) {
        continue;
      }

      Map<String, Object> payment = payments.getFirst();
      String paymentId = (String) payment.get("PaymentID");
      BigDecimal amount = toBigDecimal(payment.get("Amount"));
      Instant paidAt = parseXeroDate(payment.get("Date"));

      events.add(
          new ExternalPaymentEvent(reference, paymentId, amount, currencyCode, paidAt, "PAID"));
    }

    return events;
  }

  /** Converts a numeric value from the Xero response to BigDecimal. */
  private static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    return new BigDecimal(value.toString());
  }

  /**
   * Parses Xero's date format {@code "/Date(milliseconds+timezone)/"} to an Instant. Falls back to
   * {@link Instant#now()} if parsing fails.
   */
  static Instant parseXeroDate(Object dateValue) {
    if (dateValue == null) {
      return Instant.now();
    }
    String dateStr = dateValue.toString();

    // Xero date format: /Date(1716048000000+0000)/
    if (dateStr.startsWith("/Date(") && dateStr.endsWith(")/")) {
      String inner = dateStr.substring(6, dateStr.length() - 2);
      // Strip timezone offset if present (e.g., "+0000")
      int plusIndex = inner.indexOf('+');
      int minusIndex = inner.indexOf('-');
      String millisStr;
      if (plusIndex > 0) {
        millisStr = inner.substring(0, plusIndex);
      } else if (minusIndex > 0) {
        millisStr = inner.substring(0, minusIndex);
      } else {
        millisStr = inner;
      }
      try {
        return Instant.ofEpochMilli(Long.parseLong(millisStr));
      } catch (NumberFormatException e) {
        log.warn("Failed to parse Xero date: {}", dateStr);
        return Instant.now();
      }
    }

    // Try parsing as ISO instant
    try {
      return Instant.parse(dateStr);
    } catch (Exception e) {
      log.warn("Unrecognized Xero date format: {}", dateStr);
      return Instant.now();
    }
  }
}
