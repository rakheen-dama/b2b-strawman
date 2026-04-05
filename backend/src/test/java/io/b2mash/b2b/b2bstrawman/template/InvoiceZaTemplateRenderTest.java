package io.b2mash.b2b.b2bstrawman.template;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceZaTemplateRenderTest {
  private static final String ORG_ID = "org_invoice_za_render";

  @Autowired private MockMvc mockMvc;
  @Autowired private PdfRenderingService pdfRenderingService;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID invoiceZaTemplateId;
  private UUID testInvoiceId;

  @BeforeAll
  void setup() throws Exception {
    // Provision with accounting-za to seed the invoice-za template
    provisioningService.provisionTenant(ORG_ID, "Invoice ZA Render Test Org", "accounting-za");

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_za_render_owner",
                "inv_za_render@test.com",
                "ZA Render Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Set org tax registration number
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  settings.setTaxRegistrationNumber("4987654321");
                  orgSettingsRepository.save(settings);

                  // Find the seeded invoice-za template by pack ID + template key
                  var template =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey("accounting-za", "invoice-za")
                          .orElseThrow();
                  invoiceZaTemplateId = template.getId();

                  // Create a tax rate
                  var vatRate = new TaxRate("VAT 15%", new BigDecimal("15.00"), false, false, 10);
                  vatRate = taxRateRepository.save(vatRate);

                  // Create customer with vat_number custom field
                  var customer =
                      createActiveCustomer("SA Test Client", "client@za.test", memberIdOwner);
                  var customFields = new HashMap<String, Object>();
                  customFields.put("vat_number", "4123456789");
                  customer.setCustomFields(customFields);
                  customer = customerRepository.save(customer);
                  UUID customerId = customer.getId();

                  // Create invoice
                  var invoice =
                      new Invoice(
                          customerId,
                          "ZAR",
                          "SA Test Client",
                          "client@za.test",
                          "100 Main Road, Cape Town",
                          "Invoice ZA Render Test Org",
                          memberIdOwner);
                  invoice.updateDraft(
                      LocalDate.of(2026, 4, 30),
                      "Professional services",
                      "Net 30",
                      BigDecimal.ZERO);
                  invoice = invoiceRepository.save(invoice);
                  testInvoiceId = invoice.getId();

                  // Create line item with tax
                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Tax Advisory Services",
                          new BigDecimal("8.0000"),
                          new BigDecimal("2500.00"),
                          0);
                  BigDecimal lineTaxAmount =
                      line.getAmount()
                          .multiply(vatRate.getRate())
                          .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                  line.applyTaxRate(vatRate, lineTaxAmount);
                  invoiceLineRepository.save(line);

                  // Recalculate totals
                  invoice.recalculateTotals(line.getAmount(), true, lineTaxAmount, false);
                  invoiceRepository.save(invoice);
                }));
  }

  @Test
  void rendersVatNumbersInHtml() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var html =
                      pdfRenderingService.previewHtml(
                          invoiceZaTemplateId, testInvoiceId, memberIdOwner);

                  // Buyer VAT number (from customerVatNumber top-level variable)
                  assertThat(html).contains("4123456789");
                  // Seller VAT number (from org.taxRegistrationNumber)
                  assertThat(html).contains("4987654321");
                }));
  }

  @Test
  void rendersTaxSubtotalsInHtml() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var html =
                      pdfRenderingService.previewHtml(
                          invoiceZaTemplateId, testInvoiceId, memberIdOwner);

                  // Amount summary labels
                  assertThat(html).contains("Subtotal (excl. VAT):");
                  assertThat(html).contains("VAT (15%):");
                  assertThat(html).contains("Total Due:");

                  // Line items table headers
                  assertThat(html).contains("<th>Description</th>");
                  assertThat(html).contains("<th>Unit Price (excl. VAT)</th>");
                  assertThat(html).contains("<th>VAT</th>");
                  assertThat(html).contains("<th>Total (incl. VAT)</th>");
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
