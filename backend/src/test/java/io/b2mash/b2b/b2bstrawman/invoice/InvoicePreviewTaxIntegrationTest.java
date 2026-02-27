package io.b2mash.b2b.b2bstrawman.invoice;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxCalculationService;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoicePreviewTaxIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_preview_tax_test";
  private static final String ORG_ID_INCLUSIVE = "org_inv_preview_tax_incl";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TaxCalculationService taxCalculationService;
  @Autowired private PdfRenderingService pdfRenderingService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  /** Invoice with per-line tax (VAT + exempt lines). */
  private UUID taxInvoiceId;

  /** Invoice without per-line tax (legacy). */
  private UUID legacyInvoiceId;

  /** Invoice in a tax-inclusive tenant. */
  private UUID inclusiveInvoiceId;

  private UUID memberIdInclusive;

  @BeforeAll
  void setUp() throws Exception {
    // --- Tenant 1: Exclusive tax ---
    provisioningService.provisionTenant(ORG_ID, "Tax Preview Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_inv_preview_tax_owner",
                "inv_preview_tax_owner@test.com",
                "Tax Preview Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Configure org settings with tax registration
                      var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                      settings.setTaxRegistrationNumber("4012345678");
                      settings.setTaxRegistrationLabel("VAT Number");
                      settings.setTaxLabel("VAT");
                      orgSettingsRepository.save(settings);

                      // Create tax rates (isDefault=false to avoid unique constraint
                      // conflict with seeded default rate)
                      var vatRate = new TaxRate("VAT", new BigDecimal("15.00"), false, false, 10);
                      vatRate = taxRateRepository.save(vatRate);

                      var exemptRate = new TaxRate("Exempt", BigDecimal.ZERO, false, true, 11);
                      exemptRate = taxRateRepository.save(exemptRate);

                      // Create customer and project
                      var customer =
                          createActiveCustomer("Tax Corp", "tax@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      UUID customerId = customer.getId();

                      var project =
                          new Project("Tax Project", "Project for tax tests", memberIdOwner);
                      project = projectRepository.save(project);
                      UUID projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      // === Invoice WITH per-line tax ===
                      var taxInvoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "Tax Corp",
                              "tax@test.com",
                              "456 Tax Street, Cape Town",
                              "Tax Preview Test Org",
                              memberIdOwner);
                      taxInvoice.updateDraft(
                          LocalDate.of(2025, 2, 28),
                          "Tax invoice services",
                          "Net 30",
                          new BigDecimal("0.00"));
                      taxInvoice = invoiceRepository.save(taxInvoice);
                      taxInvoiceId = taxInvoice.getId();

                      // Line 1: VAT taxable
                      var taxLine1 =
                          new InvoiceLine(
                              taxInvoice.getId(),
                              projectId,
                              null,
                              "Backend API development",
                              new BigDecimal("10.0000"),
                              new BigDecimal("1800.00"),
                              0);
                      BigDecimal tax1 =
                          taxCalculationService.calculateLineTax(
                              taxLine1.getAmount(), vatRate.getRate(), false, false);
                      taxLine1.applyTaxRate(vatRate, tax1);
                      invoiceLineRepository.save(taxLine1);

                      // Line 2: Exempt
                      var taxLine2 =
                          new InvoiceLine(
                              taxInvoice.getId(),
                              projectId,
                              null,
                              "Consulting (exempt)",
                              new BigDecimal("5.0000"),
                              new BigDecimal("2000.00"),
                              1);
                      taxLine2.applyTaxRate(exemptRate, BigDecimal.ZERO);
                      invoiceLineRepository.save(taxLine2);

                      // Recalculate totals
                      BigDecimal subtotal = taxLine1.getAmount().add(taxLine2.getAmount());
                      taxInvoice.recalculateTotals(subtotal, true, tax1, false);
                      invoiceRepository.save(taxInvoice);

                      // === Legacy invoice (no per-line tax) ===
                      var legacyInvoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "Tax Corp",
                              "tax@test.com",
                              "456 Tax Street, Cape Town",
                              "Tax Preview Test Org",
                              memberIdOwner);
                      legacyInvoice.updateDraft(
                          LocalDate.of(2025, 3, 31),
                          "Legacy invoice",
                          "Net 30",
                          new BigDecimal("500.00"));
                      legacyInvoice = invoiceRepository.save(legacyInvoice);
                      legacyInvoiceId = legacyInvoice.getId();

                      var legacyLine =
                          new InvoiceLine(
                              legacyInvoice.getId(),
                              projectId,
                              null,
                              "Old-style service",
                              new BigDecimal("1.0000"),
                              new BigDecimal("5000.00"),
                              0);
                      invoiceLineRepository.save(legacyLine);

                      legacyInvoice.recalculateTotals(
                          legacyLine.getAmount(), false, BigDecimal.ZERO, false);
                      invoiceRepository.save(legacyInvoice);
                    }));

    // --- Tenant 2: Inclusive tax ---
    provisioningService.provisionTenant(ORG_ID_INCLUSIVE, "Tax Inclusive Org");
    planSyncService.syncPlan(ORG_ID_INCLUSIVE, "pro-plan");

    memberIdInclusive =
        UUID.fromString(
            syncMember(
                ORG_ID_INCLUSIVE,
                "user_inv_preview_tax_incl",
                "inv_preview_tax_incl@test.com",
                "Inclusive Owner",
                "owner"));

    String inclusiveSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_INCLUSIVE).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, inclusiveSchema)
        .where(RequestScopes.ORG_ID, ORG_ID_INCLUSIVE)
        .where(RequestScopes.MEMBER_ID, memberIdInclusive)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                      settings.setTaxInclusive(true);
                      settings.setTaxLabel("VAT");
                      orgSettingsRepository.save(settings);

                      var vatRate = new TaxRate("VAT", new BigDecimal("15.00"), false, false, 10);
                      vatRate = taxRateRepository.save(vatRate);

                      var customer =
                          createActiveCustomer(
                              "Inclusive Corp", "incl@test.com", memberIdInclusive);
                      customer = customerRepository.save(customer);
                      UUID customerId = customer.getId();

                      var project =
                          new Project("Inclusive Project", "Inclusive tax test", memberIdInclusive);
                      project = projectRepository.save(project);
                      UUID projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdInclusive));

                      var invoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "Inclusive Corp",
                              "incl@test.com",
                              null,
                              "Tax Inclusive Org",
                              memberIdInclusive);
                      invoice.updateDraft(
                          LocalDate.of(2025, 2, 28), "Inclusive test", "Net 30", BigDecimal.ZERO);
                      invoice = invoiceRepository.save(invoice);
                      inclusiveInvoiceId = invoice.getId();

                      var line =
                          new InvoiceLine(
                              invoice.getId(),
                              projectId,
                              null,
                              "Inclusive service",
                              new BigDecimal("1.0000"),
                              new BigDecimal("11500.00"),
                              0);
                      BigDecimal lineTax =
                          taxCalculationService.calculateLineTax(
                              line.getAmount(), vatRate.getRate(), true, false);
                      line.applyTaxRate(vatRate, lineTax);
                      invoiceLineRepository.save(line);

                      invoice.recalculateTotals(line.getAmount(), true, lineTax, true);
                      invoiceRepository.save(invoice);
                    }));
  }

  @Test
  void previewContainsTaxBreakdownSection() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + taxInvoiceId + "/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("VAT (15.00%)")));
  }

  @Test
  void previewContainsRegistrationNumber() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + taxInvoiceId + "/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("VAT Number")))
        .andExpect(content().string(containsString("4012345678")));
  }

  @Test
  void previewContainsTaxColumnOnLines() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + taxInvoiceId + "/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("VAT 15.00%")));
  }

  @Test
  void previewShowsInclusiveNoteWhenTaxInclusive() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + inclusiveInvoiceId + "/preview").with(inclusiveOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("All amounts include")))
        .andExpect(content().string(containsString("VAT")));
  }

  @Test
  void previewLegacyInvoiceShowsFlatTax() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + legacyInvoiceId + "/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(">Tax<")));
  }

  @Test
  void previewExemptLinesShowExemptLabel() throws Exception {
    mockMvc
        .perform(get("/api/invoices/" + taxInvoiceId + "/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Exempt")));
  }

  @Test
  void pdfGenerationWithTaxBreakdown() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/invoices/" + taxInvoiceId + "/preview").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String html = result.getResponse().getContentAsString();
    byte[] pdf = pdfRenderingService.htmlToPdf(html);
    assertThat(pdf).isNotEmpty();
    // PDF magic bytes: %PDF
    assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
  }

  @Test
  void previewLegacyInvoiceNoTaxColumn() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/invoices/" + legacyInvoiceId + "/preview").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String html = result.getResponse().getContentAsString();
    // Extract the body section (after <body>) to ignore CSS class definitions in <style>
    String body = html.substring(html.indexOf("<body>"));
    assertThat(body).doesNotContain("tax-col");
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_inv_preview_tax_owner")
                    .claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor inclusiveOwnerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_inv_preview_tax_incl")
                    .claim("o", Map.of("id", ORG_ID_INCLUSIVE, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  // --- Member sync helper ---

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
