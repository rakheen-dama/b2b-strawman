package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMapping;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMappingService;
import io.b2mash.b2b.b2bstrawman.integration.accounting.CustomerSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.InvoiceSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.LineItem;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XeroAccountingProviderTest {

  @Mock private XeroApiClient xeroApiClient;
  @Mock private XeroOAuthService xeroOAuthService;
  @Mock private AccountingXeroConnectionRepository connectionRepository;
  @Mock private AccountingTaxCodeMappingService taxCodeMappingService;
  @Mock private SecretStore secretStore;

  private XeroAccountingProvider provider;
  private final XeroInvoicePayloadMapper invoicePayloadMapper = new XeroInvoicePayloadMapper();

  private static final UUID ORG_INTEGRATION_ID = UUID.randomUUID();
  private static final String XERO_TENANT_ID = "xero-tenant-123";
  private static final String ACCESS_TOKEN = "test-access-token";

  @BeforeEach
  void setUp() {
    provider =
        new XeroAccountingProvider(
            xeroApiClient,
            xeroOAuthService,
            invoicePayloadMapper,
            connectionRepository,
            taxCodeMappingService,
            secretStore);
  }

  @Test
  void providerId_returnsXero() {
    assertThat(provider.providerId()).isEqualTo("xero");
  }

  @Test
  void syncInvoice_delegatesToXeroApiClientAndReturnsSuccess() {
    stubConnectedConnection();

    when(taxCodeMappingService.getByProvider("xero"))
        .thenReturn(
            List.of(
                new AccountingTaxCodeMapping(
                    "xero", "STANDARD_15", "OUTPUT2", "Standard Rate", true)));

    when(xeroApiClient.createOrUpdateInvoice(eq(XERO_TENANT_ID), any(), eq(ACCESS_TOKEN)))
        .thenReturn(Map.of("Invoices", List.of(Map.of("InvoiceID", "xero-inv-uuid-001"))));

    var request =
        new InvoiceSyncRequest(
            "INV-001",
            "Acme Corp",
            List.of(
                new LineItem(
                    "Consulting",
                    BigDecimal.ONE,
                    new BigDecimal("1500.00"),
                    BigDecimal.ZERO,
                    "STANDARD_15")),
            "ZAR",
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 17),
            "KAZI-INV-abc123",
            "customer@acme.com");

    var result = provider.syncInvoice(request);

    assertThat(result.success()).isTrue();
    assertThat(result.externalReferenceId()).isEqualTo("xero-inv-uuid-001");
    assertThat(result.errorMessage()).isNull();

    verify(xeroApiClient).createOrUpdateInvoice(eq(XERO_TENANT_ID), any(), eq(ACCESS_TOKEN));
  }

  @Test
  void syncInvoice_returnsFailureOnXeroApiException() {
    stubConnectedConnection();

    when(taxCodeMappingService.getByProvider("xero")).thenReturn(List.of());

    when(xeroApiClient.createOrUpdateInvoice(eq(XERO_TENANT_ID), any(), eq(ACCESS_TOKEN)))
        .thenThrow(new XeroApiClient.XeroApiException("Xero API error: HTTP 400"));

    var request =
        new InvoiceSyncRequest(
            "INV-002",
            "Bad Corp",
            List.of(
                new LineItem(
                    "Service", BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, null)),
            "ZAR",
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 17),
            null,
            null);

    var result = provider.syncInvoice(request);

    assertThat(result.success()).isFalse();
    assertThat(result.externalReferenceId()).isNull();
    assertThat(result.errorMessage()).isEqualTo("Xero API error: HTTP 400");
  }

  @Test
  void syncCustomer_delegatesToXeroApiClientAndReturnsSuccess() {
    stubConnectedConnection();

    when(xeroApiClient.createOrUpdateContact(eq(XERO_TENANT_ID), any(), eq(ACCESS_TOKEN)))
        .thenReturn(Map.of("Contacts", List.of(Map.of("ContactID", "xero-contact-uuid-001"))));

    var request =
        new CustomerSyncRequest(
            "Acme Corp", "billing@acme.com", "123 Main St", null, "Cape Town", "8001", "ZA");

    var result = provider.syncCustomer(request);

    assertThat(result.success()).isTrue();
    assertThat(result.externalReferenceId()).isEqualTo("xero-contact-uuid-001");
    assertThat(result.errorMessage()).isNull();

    verify(xeroApiClient).createOrUpdateContact(eq(XERO_TENANT_ID), any(), eq(ACCESS_TOKEN));
  }

  @Test
  void syncCustomer_returnsFailureOnXeroApiException() {
    stubConnectedConnection();

    when(xeroApiClient.createOrUpdateContact(eq(XERO_TENANT_ID), any(), eq(ACCESS_TOKEN)))
        .thenThrow(new XeroApiClient.XeroApiException("Xero API error: HTTP 422"));

    var request = new CustomerSyncRequest("Bad Customer", null, null, null, null, null, null);

    var result = provider.syncCustomer(request);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Xero API error: HTTP 422");
  }

  @Test
  void getPaymentsModifiedSince_mapsXeroResponseToExternalPaymentEvents() {
    stubConnectedConnection();

    Map<String, Object> xeroResponse =
        Map.of(
            "Invoices",
            List.of(
                Map.of(
                    "InvoiceID", "xero-inv-uuid",
                    "Reference", "KAZI-INV-kazi-uuid-001",
                    "Status", "PAID",
                    "CurrencyCode", "ZAR",
                    "Total", 1750.00,
                    "Payments",
                        List.of(
                            Map.of(
                                "PaymentID", "xero-pay-uuid",
                                "Amount", 1750.00,
                                "Date", "/Date(1716048000000+0000)/",
                                "Status", "AUTHORISED")))));

    when(xeroApiClient.getInvoicesModifiedSince(eq(XERO_TENANT_ID), any(), eq(ACCESS_TOKEN)))
        .thenReturn(xeroResponse);

    var since = Instant.parse("2026-05-01T00:00:00Z");
    var events = provider.getPaymentsModifiedSince(since);

    assertThat(events).hasSize(1);
    var event = events.getFirst();
    assertThat(event.externalInvoiceReference()).isEqualTo("KAZI-INV-kazi-uuid-001");
    assertThat(event.externalPaymentId()).isEqualTo("xero-pay-uuid");
    assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("1750.0"));
    assertThat(event.currency()).isEqualTo("ZAR");
    assertThat(event.status()).isEqualTo("PAID");
    // Verify the date was parsed from Xero format
    assertThat(event.paidAt()).isEqualTo(Instant.ofEpochMilli(1716048000000L));
  }

  @Test
  void getPaymentsModifiedSince_returnsEmptyForNoInvoices() {
    stubConnectedConnection();

    when(xeroApiClient.getInvoicesModifiedSince(eq(XERO_TENANT_ID), any(), eq(ACCESS_TOKEN)))
        .thenReturn(Map.of("Invoices", List.of()));

    var events = provider.getPaymentsModifiedSince(Instant.now());

    assertThat(events).isEmpty();
  }

  @Test
  void getPaymentsModifiedSince_skipsInvoicesWithNoPayments() {
    stubConnectedConnection();

    Map<String, Object> xeroResponse =
        Map.of(
            "Invoices",
            List.of(
                Map.of(
                    "InvoiceID", "xero-inv-no-pay",
                    "Reference", "KAZI-INV-orphan",
                    "Status", "PAID",
                    "CurrencyCode", "ZAR")));

    when(xeroApiClient.getInvoicesModifiedSince(eq(XERO_TENANT_ID), any(), eq(ACCESS_TOKEN)))
        .thenReturn(xeroResponse);

    var events = provider.getPaymentsModifiedSince(Instant.now());

    assertThat(events).isEmpty();
  }

  @Test
  void testConnection_returnsSuccessWhenConnectionsExist() {
    stubConnectedConnection();

    when(xeroApiClient.getConnections(ACCESS_TOKEN))
        .thenReturn(List.of(Map.of("tenantId", XERO_TENANT_ID, "tenantName", "Test Org")));

    var result = provider.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("xero");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testConnection_returnsFailureWhenNoConnections() {
    stubConnectedConnection();

    when(xeroApiClient.getConnections(ACCESS_TOKEN)).thenReturn(List.of());

    var result = provider.testConnection();

    assertThat(result.success()).isFalse();
    assertThat(result.providerName()).isEqualTo("xero");
    assertThat(result.errorMessage()).isEqualTo("No Xero tenants found");
  }

  @Test
  void testConnection_returnsFailureOnException() {
    stubConnectedConnection();

    when(xeroApiClient.getConnections(ACCESS_TOKEN))
        .thenThrow(new XeroApiClient.XeroApiException("Xero API error: HTTP 401"));

    var result = provider.testConnection();

    assertThat(result.success()).isFalse();
    assertThat(result.providerName()).isEqualTo("xero");
    assertThat(result.errorMessage()).isEqualTo("Xero API error: HTTP 401");
  }

  // ---- Xero date parsing (static helper) ----

  @Test
  void parseXeroDate_parsesMillisecondFormat() {
    Instant result = XeroAccountingProvider.parseXeroDate("/Date(1716048000000+0000)/");
    assertThat(result).isEqualTo(Instant.ofEpochMilli(1716048000000L));
  }

  @Test
  void parseXeroDate_parsesWithoutTimezoneOffset() {
    Instant result = XeroAccountingProvider.parseXeroDate("/Date(1716048000000)/");
    assertThat(result).isEqualTo(Instant.ofEpochMilli(1716048000000L));
  }

  @Test
  void mapPaymentResponse_handlesNullInvoicesArray() {
    var response = Map.<String, Object>of();
    var events = XeroAccountingProvider.mapPaymentResponse(response);
    assertThat(events).isEmpty();
  }

  // ---- Helper stubs ----

  private void stubConnectedConnection() {
    var connection =
        new AccountingXeroConnection(
            ORG_INTEGRATION_ID,
            XERO_TENANT_ID,
            "Test Xero Org",
            UUID.randomUUID(),
            Instant.now().plusSeconds(1800),
            "offline_access openid");

    when(connectionRepository.findByStatus(XeroConnectionStatus.CONNECTED))
        .thenReturn(List.of(connection));

    when(secretStore.retrieve(ORG_INTEGRATION_ID + ":xero:access")).thenReturn(ACCESS_TOKEN);
  }
}
