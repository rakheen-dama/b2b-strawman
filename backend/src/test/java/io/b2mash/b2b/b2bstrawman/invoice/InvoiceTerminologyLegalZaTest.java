package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LZKC-009 (sites 1 + 2) — firm-side "Invoice" terminology leaks on legal-za tenants:
 *
 * <ul>
 *   <li>Site 1: the invoice HTML preview header/title must read "Fee Note: DRAFT" for legal-za.
 *   <li>Site 2: the send-validation message must read "…required to send a fee note".
 * </ul>
 *
 * A generic (no-profile) tenant is asserted as a control — its copy must keep saying "Invoice".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceTerminologyLegalZaTest {

  private static final String LEGAL_ORG_ID = "org_lzkc009_legal";
  private static final String GENERIC_ORG_ID = "org_lzkc009_generic";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceRenderingService invoiceRenderingService;
  @Autowired private InvoiceValidationService invoiceValidationService;

  private String legalSchema;
  private String genericSchema;
  private UUID legalCustomerId;
  private UUID genericCustomerId;
  private UUID legalMemberId;
  private UUID genericMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(LEGAL_ORG_ID, "LZKC-009 Law Firm", "legal-za");
    legalSchema = orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).get().getSchemaName();
    legalMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_lzkc009_legal",
                "lzkc009-legal@test.com",
                "Legal Owner",
                "owner"));

    provisioningService.provisionTenant(GENERIC_ORG_ID, "LZKC-009 Generic Firm", null);
    genericSchema =
        orgSchemaMappingRepository.findByClerkOrgId(GENERIC_ORG_ID).get().getSchemaName();
    genericMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                GENERIC_ORG_ID,
                "user_lzkc009_generic",
                "lzkc009-generic@test.com",
                "Generic Owner",
                "owner"));

    // ACTIVE customers WITHOUT a tax number, so the send validation fails its tax-number check.
    legalCustomerId =
        ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
            .call(
                () ->
                    customerRepository
                        .save(
                            TestCustomerFactory.createActiveCustomer(
                                "LZKC-009 Legal Client", "lzkc009-client@test.com", legalMemberId))
                        .getId());
    genericCustomerId =
        ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
            .call(
                () ->
                    customerRepository
                        .save(
                            TestCustomerFactory.createActiveCustomer(
                                "LZKC-009 Generic Client",
                                "lzkc009-generic-client@test.com",
                                genericMemberId))
                        .getId());
  }

  // --- Site 1: invoice preview header ---

  @Test
  void renderPreview_legalZa_headerSaysFeeNote() {
    UUID invoiceId = persistDraftInvoice(legalSchema, legalCustomerId, legalMemberId, "Law Firm");

    String html =
        ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
            .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
            .call(() -> invoiceRenderingService.renderPreview(invoiceId));

    assertThat(html).contains("Fee Note: DRAFT");
    assertThat(html).doesNotContain("Invoice: DRAFT");
  }

  @Test
  void renderPreview_genericTenant_headerStillSaysInvoice() {
    UUID invoiceId =
        persistDraftInvoice(genericSchema, genericCustomerId, genericMemberId, "Generic Firm");

    String html =
        ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
            .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
            .call(() -> invoiceRenderingService.renderPreview(invoiceId));

    assertThat(html).contains("Invoice: DRAFT");
    assertThat(html).doesNotContain("Fee Note: DRAFT");
  }

  // --- Site 2: send-validation copy ---

  @Test
  void validateInvoiceSend_legalZa_taxNumberMessageSaysFeeNote() {
    var checks =
        ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
            .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
            .call(
                () ->
                    invoiceValidationService.validateInvoiceSend(
                        new Invoice(
                            legalCustomerId,
                            "ZAR",
                            "LZKC-009 Legal Client",
                            null,
                            null,
                            "LZKC-009 Law Firm",
                            legalMemberId)));

    var taxCheck =
        checks.stream()
            .filter(c -> c.name().equals("customer_tax_number"))
            .findFirst()
            .orElseThrow();
    assertThat(taxCheck.passed()).isFalse();
    assertThat(taxCheck.message()).isEqualTo("Tax Number is required to send a fee note");
  }

  @Test
  void validateInvoiceSend_genericTenant_taxNumberMessageStillSaysInvoice() {
    var checks =
        ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
            .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
            .call(
                () ->
                    invoiceValidationService.validateInvoiceSend(
                        new Invoice(
                            genericCustomerId,
                            "ZAR",
                            "LZKC-009 Generic Client",
                            null,
                            null,
                            "LZKC-009 Generic Firm",
                            genericMemberId)));

    var taxCheck =
        checks.stream()
            .filter(c -> c.name().equals("customer_tax_number"))
            .findFirst()
            .orElseThrow();
    assertThat(taxCheck.passed()).isFalse();
    assertThat(taxCheck.message()).isEqualTo("Tax Number is required to send an invoice");
  }

  // --- Helpers ---

  private UUID persistDraftInvoice(String schema, UUID customerId, UUID memberId, String orgName) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .call(
            () ->
                invoiceRepository
                    .save(
                        new Invoice(
                            customerId, "ZAR", "LZKC-009 Client", null, null, orgName, memberId))
                    .getId());
  }
}
