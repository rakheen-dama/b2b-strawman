package io.b2mash.b2b.b2bstrawman.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingSyncResult;
import io.b2mash.b2b.b2bstrawman.integration.accounting.CustomerSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.InvoiceSyncRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class IntegrationRegistryTest {

  // --- Test-only adapter stubs ---

  @IntegrationAdapter(domain = IntegrationDomain.ACCOUNTING, slug = "noop")
  static class TestNoOpAccountingAdapter implements AccountingProvider {
    @Override
    public String providerId() {
      return "noop";
    }

    @Override
    public AccountingSyncResult syncInvoice(InvoiceSyncRequest request) {
      return new AccountingSyncResult(true, "NOOP-TEST", null);
    }

    @Override
    public AccountingSyncResult syncCustomer(CustomerSyncRequest request) {
      return new AccountingSyncResult(true, "NOOP-TEST", null);
    }

    @Override
    public ConnectionTestResult testConnection() {
      return new ConnectionTestResult(true, "noop", null);
    }
  }

  @IntegrationAdapter(domain = IntegrationDomain.ACCOUNTING, slug = "noop")
  static class DuplicateAccountingAdapter implements AccountingProvider {
    @Override
    public String providerId() {
      return "duplicate";
    }

    @Override
    public AccountingSyncResult syncInvoice(InvoiceSyncRequest request) {
      return new AccountingSyncResult(false, null, "duplicate");
    }

    @Override
    public AccountingSyncResult syncCustomer(CustomerSyncRequest request) {
      return new AccountingSyncResult(false, null, "duplicate");
    }

    @Override
    public ConnectionTestResult testConnection() {
      return new ConnectionTestResult(false, null, "duplicate");
    }
  }

  @IntegrationAdapter(domain = IntegrationDomain.ACCOUNTING, slug = "xero")
  static class TestXeroAdapter implements AccountingProvider {
    @Override
    public String providerId() {
      return "xero";
    }

    @Override
    public AccountingSyncResult syncInvoice(InvoiceSyncRequest request) {
      return new AccountingSyncResult(true, "XERO-123", null);
    }

    @Override
    public AccountingSyncResult syncCustomer(CustomerSyncRequest request) {
      return new AccountingSyncResult(true, "XERO-456", null);
    }

    @Override
    public ConnectionTestResult testConnection() {
      return new ConnectionTestResult(true, "xero", null);
    }
  }

  // --- Helper to build a registry with given beans ---

  private IntegrationRegistry buildRegistry(
      Map<String, Object> beans, OrgIntegrationRepository repo) {
    var appCtx = mock(ApplicationContext.class);
    when(appCtx.getBeansWithAnnotation(IntegrationAdapter.class)).thenReturn(beans);
    return new IntegrationRegistry(appCtx, repo);
  }

  // --- Unit Tests ---

  @Nested
  class UnitTests {

    @Test
    void discoversAnnotatedBeansAtStartup() {
      var noopAdapter = new TestNoOpAccountingAdapter();
      var repo = mock(OrgIntegrationRepository.class);
      var registry = buildRegistry(Map.of("noopAccounting", noopAdapter), repo);

      var providers = registry.availableProviders(IntegrationDomain.ACCOUNTING);

      assertThat(providers).containsExactly("noop");
    }

    @Test
    void duplicateSlugThrowsIllegalStateException() {
      var adapter1 = new TestNoOpAccountingAdapter();
      var adapter2 = new DuplicateAccountingAdapter();
      var repo = mock(OrgIntegrationRepository.class);

      assertThatThrownBy(
              () -> buildRegistry(Map.of("adapter1", adapter1, "adapter2", adapter2), repo))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Duplicate");
    }

    @Test
    void resolveReturnsNoopWhenNoConfig() {
      var noopAdapter = new TestNoOpAccountingAdapter();
      var repo = mock(OrgIntegrationRepository.class);
      when(repo.findByDomain(IntegrationDomain.ACCOUNTING)).thenReturn(Optional.empty());
      var registry = buildRegistry(Map.of("noopAccounting", noopAdapter), repo);

      ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test_schema")
          .run(
              () -> {
                var result =
                    registry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
                assertThat(result).isSameAs(noopAdapter);
              });
    }

    @Test
    void resolveReturnsNoopWhenDisabled() {
      var noopAdapter = new TestNoOpAccountingAdapter();
      var xeroAdapter = new TestXeroAdapter();
      var repo = mock(OrgIntegrationRepository.class);

      var disabledIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
      // enabled defaults to false in the constructor

      when(repo.findByDomain(IntegrationDomain.ACCOUNTING))
          .thenReturn(Optional.of(disabledIntegration));

      var registry =
          buildRegistry(Map.of("noopAccounting", noopAdapter, "xeroAccounting", xeroAdapter), repo);

      ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test_schema")
          .run(
              () -> {
                var result =
                    registry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
                assertThat(result).isSameAs(noopAdapter);
              });
    }

    @Test
    void resolveNeverThrowsNpeForMissingDomain() {
      var noopAdapter = new TestNoOpAccountingAdapter();
      var repo = mock(OrgIntegrationRepository.class);
      when(repo.findByDomain(IntegrationDomain.ACCOUNTING)).thenReturn(Optional.empty());
      var registry = buildRegistry(Map.of("noopAccounting", noopAdapter), repo);

      ScopedValue.where(RequestScopes.TENANT_ID, "tenant_npe_test")
          .run(
              () -> {
                // This must NOT throw NPE -- the EMPTY sentinel prevents Caffeine from
                // rejecting a null loader result.
                var result =
                    registry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
                assertThat(result).isNotNull();
                assertThat(result).isSameAs(noopAdapter);
              });
    }

    @Test
    void resolveReturnsConfiguredAdapterWhenEnabled() {
      var noopAdapter = new TestNoOpAccountingAdapter();
      var xeroAdapter = new TestXeroAdapter();
      var repo = mock(OrgIntegrationRepository.class);

      var enabledIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
      enabledIntegration.enable();

      when(repo.findByDomain(IntegrationDomain.ACCOUNTING))
          .thenReturn(Optional.of(enabledIntegration));

      var registry =
          buildRegistry(Map.of("noopAccounting", noopAdapter, "xeroAccounting", xeroAdapter), repo);

      ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test_schema")
          .run(
              () -> {
                var result =
                    registry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
                assertThat(result).isSameAs(xeroAdapter);
              });
    }

    @Test
    void availableProvidersReturnsEmptyForUnknownDomain() {
      var repo = mock(OrgIntegrationRepository.class);
      var registry = buildRegistry(Map.of(), repo);

      var providers = registry.availableProviders(IntegrationDomain.AI);

      assertThat(providers).isEmpty();
    }
  }
}
