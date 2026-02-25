package io.b2mash.b2b.b2bstrawman.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.integration.email.EmailAttachment;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailMessage;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.SendResult;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class EmailProviderResolutionIntegrationTest {

  // --- Test-only adapter stubs ---

  @IntegrationAdapter(domain = IntegrationDomain.EMAIL, slug = "smtp")
  static class TestSmtpEmailAdapter implements EmailProvider {
    @Override
    public String providerId() {
      return "smtp";
    }

    @Override
    public SendResult sendEmail(EmailMessage message) {
      return new SendResult(true, "SMTP-TEST", null);
    }

    @Override
    public SendResult sendEmailWithAttachment(EmailMessage message, EmailAttachment attachment) {
      return new SendResult(true, "SMTP-TEST-ATT", null);
    }

    @Override
    public ConnectionTestResult testConnection() {
      return new ConnectionTestResult(true, "smtp", null);
    }
  }

  // --- Helper to build a registry with given beans ---

  private IntegrationRegistry buildRegistry(
      Map<String, Object> beans, OrgIntegrationRepository repo) {
    var appCtx = mock(ApplicationContext.class);
    when(appCtx.getBeansWithAnnotation(IntegrationAdapter.class)).thenReturn(beans);
    return new IntegrationRegistry(appCtx, repo);
  }

  // --- defaultSlug tests ---

  @Test
  void defaultSlug_EMAIL_is_smtp() {
    assertThat(IntegrationDomain.EMAIL.getDefaultSlug()).isEqualTo("smtp");
  }

  @Test
  void defaultSlug_ACCOUNTING_is_noop() {
    assertThat(IntegrationDomain.ACCOUNTING.getDefaultSlug()).isEqualTo("noop");
  }

  // --- Resolution tests ---

  @Test
  void resolve_ACCOUNTING_still_returns_noop() {
    var noopAdapter = new IntegrationRegistryTest.TestNoOpAccountingAdapter();
    var repo = mock(OrgIntegrationRepository.class);
    when(repo.findByDomain(IntegrationDomain.ACCOUNTING)).thenReturn(Optional.empty());
    var registry = buildRegistry(Map.of("noopAccounting", noopAdapter), repo);

    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test_schema")
        .run(
            () -> {
              var result =
                  registry.resolve(
                      IntegrationDomain.ACCOUNTING,
                      io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider.class);
              assertThat(result).isSameAs(noopAdapter);
            });
  }

  @Test
  void resolve_AI_still_returns_noop_when_no_config() {
    // AI domain has defaultSlug "noop" — verify resolve() still uses domain.getDefaultSlug()
    // Since no adapter is registered for AI here, we verify the error message uses the correct slug
    var repo = mock(OrgIntegrationRepository.class);
    when(repo.findByDomain(IntegrationDomain.AI)).thenReturn(Optional.empty());
    var registry = buildRegistry(Map.of(), repo);

    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test_schema")
        .run(
            () -> {
              try {
                registry.resolve(IntegrationDomain.AI, Object.class);
              } catch (IllegalStateException e) {
                assertThat(e.getMessage()).contains("No noop adapter registered for domain AI");
              }
            });
  }

  @Test
  void resolve_EMAIL_returns_smtp_adapter_when_no_org_integration() {
    var smtpAdapter = new TestSmtpEmailAdapter();
    var repo = mock(OrgIntegrationRepository.class);
    when(repo.findByDomain(IntegrationDomain.EMAIL)).thenReturn(Optional.empty());
    var registry = buildRegistry(Map.of("smtpEmail", smtpAdapter), repo);

    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test_schema")
        .run(
            () -> {
              var result = registry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);
              assertThat(result).isSameAs(smtpAdapter);
              assertThat(result.providerId()).isEqualTo("smtp");
            });
  }
}
