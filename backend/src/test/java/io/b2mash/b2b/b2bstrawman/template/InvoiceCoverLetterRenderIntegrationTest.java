package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regression test for LZKC-010: the pack-seeded "Invoice Cover Letter" template rendered "Invoice
 * Number:" blank because the template placeholder key was {@code invoice.number} while {@link
 * InvoiceContextBuilder} populates {@code invoice.invoiceNumber}. {@code
 * TiptapRenderer#resolveVariable} silently renders missing keys as an empty string, so the typo
 * produced a blank field instead of an error.
 *
 * <p>Renders the tenant-seeded template (not a hand-built fixture) against real {@link
 * InvoiceContextBuilder} output, so the assertion covers the seeder → context-builder → renderer
 * pipeline end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceCoverLetterRenderIntegrationTest {

  private static final String ORG_ID = "org_invoice_cover_letter_render_test";
  private static final String LOGO_S3_KEY = "org/invoice-cover-letter-test/branding/logo.png";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceContextBuilder invoiceContextBuilder;
  @Autowired private TiptapRenderer tiptapRenderer;
  @Autowired private PdfRenderingService pdfRenderingService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Cover Letter Render Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_icl_owner", "icl_owner@test.com", "ICL Owner", "owner"));
  }

  @Test
  void coverLetterRendersInvoiceNumberAndTotalFromInvoiceContext() {
    var html = new AtomicReference<String>();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          customerRepository.save(
                              TestCustomerFactory.createActiveCustomer(
                                  "Acme (Pty) Ltd", "billing@acme.test", memberId));

                      var invoice =
                          new Invoice(
                              customer.getId(),
                              "ZAR",
                              "Acme (Pty) Ltd",
                              "billing@acme.test",
                              "1 Main Road, Cape Town",
                              "Render Test Org",
                              memberId);
                      invoice.recalculateTotals(
                          new BigDecimal("1250.00"), false, BigDecimal.ZERO, false);
                      invoice.approve("INV-9001", memberId);
                      invoice = invoiceRepository.save(invoice);

                      var template =
                          documentTemplateRepository
                              .findByPackIdAndPackTemplateKey("common", "invoice-cover-letter")
                              .orElseThrow();

                      Map<String, Object> context =
                          invoiceContextBuilder.buildContext(invoice.getId(), memberId);

                      html.set(
                          tiptapRenderer.render(
                              template.getContent(), context, Map.of(), template.getCss()));
                    }));

    // LZKC-010: "Invoice Number:" must be followed by the actual invoice number, not blank.
    assertThat(html.get()).contains("INV-9001");
    // The sibling "Total Amount:" placeholder (invoice.total) resolves from the same context.
    assertThat(html.get()).contains("1250.00");
  }

  /**
   * LZKC-007 (part 1): the pack cover letter carries an {@code org.logoUrl} letterhead node,
   * rendered through the registry-driven {@code image} format hint on the {@link
   * PdfRenderingService} preview/generate pipeline. With a branding logo configured, the rendered
   * letter must embed the presigned logo URL; with none, it must render cleanly without an {@code
   * <img>} element.
   */
  @Test
  void coverLetterRendersLetterheadLogoViaPdfRenderingPipeline() {
    var withLogo = new AtomicReference<String>();
    var withoutLogo = new AtomicReference<String>();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          customerRepository.save(
                              TestCustomerFactory.createActiveCustomer(
                                  "Logo Client (Pty) Ltd", "logo@acme.test", memberId));

                      var invoice =
                          new Invoice(
                              customer.getId(),
                              "ZAR",
                              "Logo Client (Pty) Ltd",
                              "logo@acme.test",
                              "2 Main Road, Cape Town",
                              "Render Test Org",
                              memberId);
                      invoice.recalculateTotals(
                          new BigDecimal("900.00"), false, BigDecimal.ZERO, false);
                      invoice.approve("INV-9002", memberId);
                      invoice = invoiceRepository.save(invoice);

                      var template =
                          documentTemplateRepository
                              .findByPackIdAndPackTemplateKey("common", "invoice-cover-letter")
                              .orElseThrow();

                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.getBranding().setLogoS3Key(LOGO_S3_KEY);
                      orgSettingsRepository.save(settings);
                      withLogo.set(
                          pdfRenderingService.previewHtml(
                              template.getId(), invoice.getId(), memberId));

                      settings.getBranding().setLogoS3Key(null);
                      orgSettingsRepository.save(settings);
                      withoutLogo.set(
                          pdfRenderingService.previewHtml(
                              template.getId(), invoice.getId(), memberId));
                    }));

    assertThat(withLogo.get()).contains("<img class=\"letterhead-logo\"");
    assertThat(withLogo.get()).contains("http://test-storage/test-bucket/" + LOGO_S3_KEY);
    assertThat(withoutLogo.get()).doesNotContain("<img");
    assertThat(withoutLogo.get()).contains("INV-9002");
  }
}
