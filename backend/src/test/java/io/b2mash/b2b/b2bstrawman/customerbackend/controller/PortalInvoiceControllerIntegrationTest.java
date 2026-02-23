package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalInvoiceControllerIntegrationTest {

  private static final String ORG_ID = "org_portal_invoice_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalReadModelRepository readModelRepo;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @MockitoBean private StorageService storageService;

  private UUID customerId;
  private UUID memberId;
  private UUID invoiceWithLines;
  private UUID invoiceWithPdf;
  private UUID invoiceNoPdf;
  private UUID otherCustomerInvoiceId;
  private String portalToken;
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Invoice Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    var syncResult =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_inv_portal_owner",
                          "email": "inv_portal_owner@test.com",
                          "name": "Invoice Portal Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    memberId = UUID.fromString(memberIdStr);
    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    // Create primary customer + portal contact
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Invoice Portal Customer", "inv-portal@test.com", null, null, null, memberId);
              customerId = customer.getId();
              portalContactService.createContact(
                  ORG_ID,
                  customerId,
                  "inv-portal-contact@test.com",
                  "Invoice Contact",
                  PortalContact.ContactRole.PRIMARY);
            });

    portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    // Create other customer (for isolation tests)
    UUID[] otherCustomerIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var other =
                  customerService.createCustomer(
                      "Other Invoice Customer", "other-inv@test.com", null, null, null, memberId);
              otherCustomerIdHolder[0] = other.getId();
            });

    // Seed invoices directly in portal read-model
    invoiceWithLines = UUID.randomUUID();
    invoiceWithPdf = UUID.randomUUID();
    invoiceNoPdf = UUID.randomUUID();
    otherCustomerInvoiceId = UUID.randomUUID();

    readModelRepo.upsertPortalInvoice(
        invoiceWithLines,
        ORG_ID,
        customerId,
        "INV-2026-001",
        "SENT",
        LocalDate.of(2026, 1, 15),
        LocalDate.of(2026, 2, 15),
        new BigDecimal("1000.00"),
        new BigDecimal("150.00"),
        new BigDecimal("1150.00"),
        "ZAR",
        "Test invoice");
    readModelRepo.upsertPortalInvoiceLine(
        UUID.randomUUID(),
        invoiceWithLines,
        "Development work",
        new BigDecimal("10.0000"),
        new BigDecimal("100.00"),
        new BigDecimal("1000.00"),
        0);
    readModelRepo.upsertPortalInvoiceLine(
        UUID.randomUUID(),
        invoiceWithLines,
        "Tax",
        new BigDecimal("1.0000"),
        new BigDecimal("150.00"),
        new BigDecimal("150.00"),
        1);

    readModelRepo.upsertPortalInvoice(
        invoiceWithPdf,
        ORG_ID,
        customerId,
        "INV-2026-002",
        "PAID",
        LocalDate.of(2026, 2, 1),
        LocalDate.of(2026, 3, 1),
        new BigDecimal("500.00"),
        BigDecimal.ZERO,
        new BigDecimal("500.00"),
        "ZAR",
        null);

    readModelRepo.upsertPortalInvoice(
        invoiceNoPdf,
        ORG_ID,
        customerId,
        "INV-2026-003",
        "SENT",
        LocalDate.of(2026, 2, 10),
        LocalDate.of(2026, 3, 10),
        new BigDecimal("200.00"),
        BigDecimal.ZERO,
        new BigDecimal("200.00"),
        "ZAR",
        null);

    readModelRepo.upsertPortalInvoice(
        otherCustomerInvoiceId,
        ORG_ID,
        otherCustomerIdHolder[0],
        "INV-2026-OTHER",
        "SENT",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 1),
        new BigDecimal("999.00"),
        BigDecimal.ZERO,
        new BigDecimal("999.00"),
        "ZAR",
        null);

    // Seed GeneratedDocument for invoiceWithPdf (in tenant schema)
    // Must create a DocumentTemplate first to satisfy FK constraint
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var template =
                  new DocumentTemplate(
                      TemplateEntityType.INVOICE,
                      "Invoice Template",
                      "invoice-template",
                      TemplateCategory.COVER_LETTER,
                      "<html>Invoice</html>");
              documentTemplateRepository.save(template);

              var doc =
                  new GeneratedDocument(
                      template.getId(),
                      TemplateEntityType.INVOICE,
                      invoiceWithPdf,
                      "invoice-2026-002.pdf",
                      "invoices/" + invoiceWithPdf + "/invoice.pdf",
                      12345L,
                      memberId);
              generatedDocumentRepository.save(doc);
            });

    // Mock StorageService to return a predictable URL
    when(storageService.generateDownloadUrl(any(String.class), any()))
        .thenReturn(
            new PresignedUrl(
                "https://s3.example.com/invoice.pdf", Instant.now().plusSeconds(3600)));
  }

  @Test
  void list_invoices_returns_customer_invoices_only() throws Exception {
    mockMvc
        .perform(get("/portal/invoices").header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.invoiceNumber == 'INV-2026-001')]").exists())
        .andExpect(jsonPath("$[?(@.invoiceNumber == 'INV-2026-OTHER')]").doesNotExist());
  }

  @Test
  void list_invoices_empty_state() throws Exception {
    UUID[] freshCustomerHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var c =
                  customerService.createCustomer(
                      "Fresh Portal Customer", "fresh-portal@test.com", null, null, null, memberId);
              freshCustomerHolder[0] = c.getId();
              portalContactService.createContact(
                  ORG_ID,
                  c.getId(),
                  "fresh-contact@test.com",
                  "Fresh Contact",
                  PortalContact.ContactRole.GENERAL);
            });
    String freshToken = portalJwtService.issueToken(freshCustomerHolder[0], ORG_ID);

    mockMvc
        .perform(get("/portal/invoices").header("Authorization", "Bearer " + freshToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void get_invoice_detail_with_lines() throws Exception {
    mockMvc
        .perform(
            get("/portal/invoices/{id}", invoiceWithLines)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoiceNumber").value("INV-2026-001"))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.total").value(1150.00))
        .andExpect(jsonPath("$.currency").value("ZAR"))
        .andExpect(jsonPath("$.notes").value("Test invoice"))
        .andExpect(jsonPath("$.lines.length()").value(2))
        .andExpect(jsonPath("$.lines[0].description").value("Development work"))
        .andExpect(jsonPath("$.lines[0].sortOrder").value(0));
  }

  @Test
  void get_invoice_detail_wrong_customer_returns_404() throws Exception {
    mockMvc
        .perform(
            get("/portal/invoices/{id}", otherCustomerInvoiceId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void download_returns_presigned_url() throws Exception {
    mockMvc
        .perform(
            get("/portal/invoices/{id}/download", invoiceWithPdf)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.downloadUrl").value("https://s3.example.com/invoice.pdf"));
  }

  @Test
  void download_no_pdf_returns_404() throws Exception {
    mockMvc
        .perform(
            get("/portal/invoices/{id}/download", invoiceNoPdf)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void unauthorized_returns_401() throws Exception {
    mockMvc.perform(get("/portal/invoices")).andExpect(status().isUnauthorized());
  }
}
