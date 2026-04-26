package io.b2mash.b2b.b2bstrawman.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that {@link EmailContextBuilder} threads the firm's vertical profile through {@link
 * EmailTerminology}, exposing {@code terminology}, {@code invoiceTerm}, {@code invoiceTermLower},
 * and the plural variants in the email base context (GAP-L-65).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailContextBuilderTest {

  private static final String LEGAL_ORG_ID = "org_email_ctx_legal";
  private static final String LEGAL_ORG_NAME = "Email Ctx Law Firm";
  private static final String GENERIC_ORG_ID = "org_email_ctx_generic";
  private static final String GENERIC_ORG_NAME = "Email Ctx Generic Firm";

  @Autowired private EmailContextBuilder emailContextBuilder;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;

  private String legalSchema;
  private String genericSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(LEGAL_ORG_ID, LEGAL_ORG_NAME, "legal-za");
    legalSchema = orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).get().getSchemaName();

    provisioningService.provisionTenant(GENERIC_ORG_ID, GENERIC_ORG_NAME, null);
    genericSchema =
        orgSchemaMappingRepository.findByClerkOrgId(GENERIC_ORG_ID).get().getSchemaName();
    // Defensive: ensure verticalProfile remains null for the generic tenant.
    ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
        .run(
            () -> {
              OrgSettings settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
              settings.setVerticalProfile(null);
              orgSettingsRepository.save(settings);
            });
  }

  @Test
  void buildBaseContext_legalZa_resolvesInvoiceTermToFeeNote() {
    Map<String, Object> context =
        ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
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
  }

  @Test
  void buildBaseContext_genericTenant_fallsBackToInvoice() {
    Map<String, Object> context =
        ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
            .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
            .call(() -> emailContextBuilder.buildBaseContext("Customer", null));

    @SuppressWarnings("unchecked")
    Map<String, String> terminology = (Map<String, String>) context.get("terminology");
    assertThat(terminology).isEmpty();

    assertThat(context).containsEntry("invoiceTerm", "Invoice");
    assertThat(context).containsEntry("invoiceTermLower", "invoice");
    assertThat(context).containsEntry("invoiceTermPlural", "Invoices");
    assertThat(context).containsEntry("invoiceTermPluralLower", "invoices");
  }

  @Test
  void buildBaseContext_unknownVerticalProfile_fallsBackToIdentity() {
    // Set an unknown vertical profile -- EmailTerminology should return an empty map and the
    // convenience keys should fall back to identity.
    ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
        .run(
            () -> {
              OrgSettings settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
              settings.setVerticalProfile("retail-uk");
              orgSettingsRepository.save(settings);
            });

    Map<String, Object> context =
        ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
            .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
            .call(() -> emailContextBuilder.buildBaseContext("Customer", null));

    @SuppressWarnings("unchecked")
    Map<String, String> terminology = (Map<String, String>) context.get("terminology");
    assertThat(terminology).isEmpty();
    assertThat(context).containsEntry("invoiceTerm", "Invoice");
    assertThat(context).containsEntry("invoiceTermLower", "invoice");

    // Reset for hygiene if other tests run after.
    ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
        .run(
            () -> {
              OrgSettings settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
              settings.setVerticalProfile(null);
              orgSettingsRepository.save(settings);
            });
  }
}
