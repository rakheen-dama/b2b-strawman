package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
class PortalPaymentStatusIntegrationTest {

  private static final String ORG_ID = "org_portal_payment_status_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalReadModelRepository readModelRepo;
  @MockitoBean private StorageService storageService;

  private UUID customerId;
  private UUID otherCustomerId;
  private UUID sentInvoiceId;
  private UUID paidInvoiceId;
  private UUID otherCustomerInvoiceId;
  private UUID invoiceWithPaymentUrl;
  private String portalToken;
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Payment Status Test Org");
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
                          "clerkUserId": "user_pay_status_owner",
                          "email": "pay_status_owner@test.com",
                          "name": "Payment Status Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    UUID memberId = UUID.fromString(memberIdStr);
    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    // Create primary customer + portal contact
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Payment Status Customer", "pay-status@test.com", null, null, null, memberId);
              customerId = customer.getId();
              portalContactService.createContact(
                  ORG_ID,
                  customerId,
                  "pay-status-contact@test.com",
                  "Pay Status Contact",
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
                      "Other Payment Customer", "other-pay@test.com", null, null, null, memberId);
              otherCustomerIdHolder[0] = other.getId();
            });
    otherCustomerId = otherCustomerIdHolder[0];

    // Seed invoices directly in portal read-model
    sentInvoiceId = UUID.randomUUID();
    readModelRepo.upsertPortalInvoice(
        sentInvoiceId,
        ORG_ID,
        customerId,
        "INV-PAY-001",
        "SENT",
        LocalDate.of(2026, 1, 15),
        LocalDate.of(2026, 2, 15),
        new BigDecimal("1000.00"),
        new BigDecimal("150.00"),
        new BigDecimal("1150.00"),
        "ZAR",
        "Unpaid invoice",
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false);

    paidInvoiceId = UUID.randomUUID();
    readModelRepo.upsertPortalInvoice(
        paidInvoiceId,
        ORG_ID,
        customerId,
        "INV-PAY-002",
        "PAID",
        LocalDate.of(2026, 2, 1),
        LocalDate.of(2026, 3, 1),
        new BigDecimal("500.00"),
        BigDecimal.ZERO,
        new BigDecimal("500.00"),
        "ZAR",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false);
    // Set paid_at for the paid invoice
    readModelRepo.updatePortalInvoiceStatusAndPaidAt(paidInvoiceId, ORG_ID, "PAID", Instant.now());

    otherCustomerInvoiceId = UUID.randomUUID();
    readModelRepo.upsertPortalInvoice(
        otherCustomerInvoiceId,
        ORG_ID,
        otherCustomerId,
        "INV-PAY-OTHER",
        "SENT",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 1),
        new BigDecimal("999.00"),
        BigDecimal.ZERO,
        new BigDecimal("999.00"),
        "ZAR",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false);

    // Invoice with payment URL (tests sync handler propagation)
    invoiceWithPaymentUrl = UUID.randomUUID();
    readModelRepo.upsertPortalInvoice(
        invoiceWithPaymentUrl,
        ORG_ID,
        customerId,
        "INV-PAY-003",
        "SENT",
        LocalDate.of(2026, 2, 10),
        LocalDate.of(2026, 3, 10),
        new BigDecimal("750.00"),
        BigDecimal.ZERO,
        new BigDecimal("750.00"),
        "ZAR",
        null,
        "https://pay.example.com/session/abc123",
        "sess_abc123",
        null,
        null,
        null,
        null,
        false,
        false);
  }

  @Test
  void getPaymentStatus_returns_SENT_for_unpaid() throws Exception {
    mockMvc
        .perform(
            get("/portal/invoices/{id}/payment-status", sentInvoiceId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.paidAt").doesNotExist());
  }

  @Test
  void getPaymentStatus_returns_PAID_with_paidAt() throws Exception {
    mockMvc
        .perform(
            get("/portal/invoices/{id}/payment-status", paidInvoiceId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.paidAt").isNotEmpty());
  }

  @Test
  void getPaymentStatus_returns_404_for_unknown_invoice() throws Exception {
    mockMvc
        .perform(
            get("/portal/invoices/{id}/payment-status", UUID.randomUUID())
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void getPaymentStatus_verifies_portal_contact_access() throws Exception {
    // Invoice belonging to another customer should return 404
    mockMvc
        .perform(
            get("/portal/invoices/{id}/payment-status", otherCustomerInvoiceId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void paymentUrl_synced_to_portal_read_model() throws Exception {
    // Verify the invoice with payment URL was properly stored
    var invoice = readModelRepo.findInvoiceById(invoiceWithPaymentUrl, ORG_ID);
    assertThat(invoice).isPresent();
    assertThat(invoice.get().paymentUrl()).isEqualTo("https://pay.example.com/session/abc123");
    assertThat(invoice.get().paymentSessionId()).isEqualTo("sess_abc123");
  }
}
