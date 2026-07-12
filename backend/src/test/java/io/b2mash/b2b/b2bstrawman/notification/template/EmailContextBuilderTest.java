package io.b2mash.b2b.b2bstrawman.notification.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link EmailContextBuilder} threads the firm's vertical profile through {@link
 * EmailTerminology}, exposing {@code terminology}, {@code invoiceTerm}, {@code invoiceTermLower},
 * and the plural variants in the email base context (GAP-L-65).
 *
 * <p>Plain unit test: the builder's dependencies are mocked and the vertical profile is stubbed on
 * an in-memory {@link OrgSettings}. The DB round-trip of {@code verticalProfile} is covered by the
 * legal-za integration tests (e.g. {@code InvoiceTerminologyLegalZaTest}).
 */
class EmailContextBuilderTest {

  private static final String LEGAL_ORG_ID = "org_email_ctx_legal";
  private static final String GENERIC_ORG_ID = "org_email_ctx_generic";
  private static final String LEGAL_SCHEMA = "tenant_email_ctx_legal";
  private static final String GENERIC_SCHEMA = "tenant_email_ctx_generic";

  private final OrgSettingsRepository orgSettingsRepository = mock(OrgSettingsRepository.class);
  private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
  private final EmailContextBuilder emailContextBuilder =
      new EmailContextBuilder(
          orgSettingsRepository,
          organizationRepository,
          mock(StorageService.class),
          new EmailTerminology(),
          "http://localhost:3000",
          "Kazi");

  /** Stubs the tenant's {@link OrgSettings} with the given vertical profile (null = generic). */
  private void stubVerticalProfile(String verticalProfile) {
    OrgSettings settings = new OrgSettings("ZAR");
    settings.setVerticalProfile(verticalProfile);
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));
  }

  @Test
  void buildBaseContext_legalZa_resolvesInvoiceTermToFeeNote() {
    stubVerticalProfile("legal-za");

    Map<String, Object> context =
        ScopedValue.where(RequestScopes.TENANT_ID, LEGAL_SCHEMA)
            .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
            .call(() -> emailContextBuilder.buildBaseContext("Customer", null));

    assertThat(context).containsKey("terminology");
    @SuppressWarnings("unchecked")
    Map<String, String> terminology = (Map<String, String>) context.get("terminology");
    assertThat(terminology).containsEntry("Invoice", "Fee Note");
    assertThat(terminology).containsEntry("invoices", "fee notes");

    assertThat(context).containsEntry("invoiceTerm", "Fee Note");
    assertThat(context).containsEntry("invoiceTermLower", "fee note");
    assertThat(context).containsEntry("invoiceTermPlural", "Fee Notes");
    assertThat(context).containsEntry("invoiceTermPluralLower", "fee notes");

    // LZKC-004 — proposal nouns resolve to engagement-letter vocabulary
    assertThat(terminology).containsEntry("Proposal", "Engagement Letter");
    assertThat(context).containsEntry("proposalTerm", "Engagement Letter");
    assertThat(context).containsEntry("proposalTermLower", "engagement letter");
    assertThat(context).containsEntry("proposalTermLowerWithArticle", "an engagement letter");
  }

  @Test
  void buildBaseContext_genericTenant_fallsBackToInvoice() {
    stubVerticalProfile(null);

    Map<String, Object> context =
        ScopedValue.where(RequestScopes.TENANT_ID, GENERIC_SCHEMA)
            .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
            .call(() -> emailContextBuilder.buildBaseContext("Customer", null));

    @SuppressWarnings("unchecked")
    Map<String, String> terminology = (Map<String, String>) context.get("terminology");
    assertThat(terminology).isEmpty();

    assertThat(context).containsEntry("invoiceTerm", "Invoice");
    assertThat(context).containsEntry("invoiceTermLower", "invoice");
    assertThat(context).containsEntry("invoiceTermPlural", "Invoices");
    assertThat(context).containsEntry("invoiceTermPluralLower", "invoices");

    // LZKC-004 — proposal keys fall back to identity for generic tenants
    assertThat(context).containsEntry("proposalTerm", "Proposal");
    assertThat(context).containsEntry("proposalTermLower", "proposal");
    assertThat(context).containsEntry("proposalTermLowerWithArticle", "a proposal");
  }

  @Test
  void buildBaseContext_exposesOrgScopedAppUrl() {
    stubVerticalProfile(null);

    Map<String, Object> context =
        ScopedValue.where(RequestScopes.TENANT_ID, GENERIC_SCHEMA)
            .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
            .call(() -> emailContextBuilder.buildBaseContext("Customer", null));

    // LZKC-022 — appUrl stays the bare base (safe non-404 fallback); orgAppUrl carries the
    // /org/{slug} prefix used by all firm-side CTA deep links.
    assertThat(context).containsEntry("appUrl", "http://localhost:3000");
    assertThat(context).containsEntry("orgAppUrl", "http://localhost:3000/org/" + GENERIC_ORG_ID);
  }

  @Test
  void buildBaseContext_withoutOrgScope_orgAppUrlFallsBackToBase() {
    stubVerticalProfile(null);

    Map<String, Object> context =
        ScopedValue.where(RequestScopes.TENANT_ID, GENERIC_SCHEMA)
            .call(() -> emailContextBuilder.buildBaseContext("Customer", null));

    assertThat(context).containsEntry("orgAppUrl", "http://localhost:3000");
  }

  @Test
  void buildBaseContext_unknownVerticalProfile_fallsBackToIdentity() {
    // An unknown vertical profile -- EmailTerminology should return an empty map and the
    // convenience keys should fall back to identity.
    stubVerticalProfile("retail-uk");

    Map<String, Object> context =
        ScopedValue.where(RequestScopes.TENANT_ID, GENERIC_SCHEMA)
            .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
            .call(() -> emailContextBuilder.buildBaseContext("Customer", null));

    @SuppressWarnings("unchecked")
    Map<String, String> terminology = (Map<String, String>) context.get("terminology");
    assertThat(terminology).isEmpty();
    assertThat(context).containsEntry("invoiceTerm", "Invoice");
    assertThat(context).containsEntry("invoiceTermLower", "invoice");
  }
}
