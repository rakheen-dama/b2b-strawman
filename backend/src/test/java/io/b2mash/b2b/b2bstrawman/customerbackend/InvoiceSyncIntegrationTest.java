package io.b2mash.b2b.b2bstrawman.customerbackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests verifying the invoice sync pipeline: staff API call -> InvoiceSyncEvent ->
 * PortalEventHandler -> portal read-model rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoiceSyncIntegrationTest {
  private static final String ORG_ID = "org_invoice_sync_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PortalReadModelRepository readModelRepo;

  private String projectId;
  private String customerId;
  private String invoiceId;
  private String voidInvoiceId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Sync Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_inv_owner", "inv_owner@test.com", "Inv Owner", "owner");

    projectId =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner"),
            "Invoice Test Project",
            "For invoice sync tests");
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner"),
            "Invoice Test Customer",
            "inv_cust@test.com");
    // Fill prerequisite custom fields for invoice/proposal checks
    fillPrerequisiteFields(customerId, "Invoice Test Customer", "inv_cust@test.com");
    transitionCustomerToActive(customerId);

    // Link project to customer to enable portal sync
    linkProjectToCustomer(customerId, projectId);
  }

  @Test
  @Order(1)
  void draftInvoice_notSyncedToPortal() throws Exception {
    // Create a draft invoice
    invoiceId = createInvoice(customerId, "ZAR");

    // Verify DRAFT does NOT appear in portal
    var invoices = readModelRepo.findInvoicesByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(invoices).noneMatch(i -> i.id().equals(UUID.fromString(invoiceId)));
  }

  @Test
  @Order(2)
  void approvedInvoice_notSyncedToPortal() throws Exception {
    // Add a line item so we can approve
    addLineItem(invoiceId, "Test line item", "1", "100.00", 0);

    // Approve the invoice
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner")))
        .andExpect(status().isOk());

    // Verify APPROVED does NOT appear in portal
    var invoices = readModelRepo.findInvoicesByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(invoices).noneMatch(i -> i.id().equals(UUID.fromString(invoiceId)));
  }

  @Test
  @Order(3)
  void markSent_syncsInvoiceToPortal() throws Exception {
    // Mark as sent
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner")))
        .andExpect(status().isOk());

    // Verify portal contains the invoice with status SENT
    var invoices = readModelRepo.findInvoicesByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(invoices)
        .anyMatch(i -> i.id().equals(UUID.fromString(invoiceId)) && "SENT".equals(i.status()));
  }

  @Test
  @Order(4)
  void sentInvoiceHasCorrectLineItems() throws Exception {
    // Verify line items were synced
    var lines = readModelRepo.findInvoiceLinesByInvoice(UUID.fromString(invoiceId));
    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst().description()).isEqualTo("Test line item");
    assertThat(lines.getFirst().amount()).isEqualByComparingTo("100.00");
  }

  @Test
  @Order(5)
  void markPaid_updatesPortalStatus() throws Exception {
    // Record payment
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner")))
        .andExpect(status().isOk());

    // Verify status updated to PAID in portal
    var invoice = readModelRepo.findInvoiceById(UUID.fromString(invoiceId), ORG_ID);
    assertThat(invoice).isPresent();
    assertThat(invoice.get().status()).isEqualTo("PAID");
  }

  @Test
  @Order(6)
  void voidInvoice_removesFromPortal() throws Exception {
    // Create, add line, approve, and send a separate invoice for voiding
    voidInvoiceId = createInvoice(customerId, "ZAR");
    addLineItem(voidInvoiceId, "Void test line", "2", "50.00", 0);

    mockMvc
        .perform(
            post("/api/invoices/" + voidInvoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/invoices/" + voidInvoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner")))
        .andExpect(status().isOk());

    // Verify it was synced
    var beforeVoid = readModelRepo.findInvoiceById(UUID.fromString(voidInvoiceId), ORG_ID);
    assertThat(beforeVoid).isPresent();

    // Void it
    mockMvc
        .perform(
            post("/api/invoices/" + voidInvoiceId + "/void")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner")))
        .andExpect(status().isOk());

    // Verify it's removed from portal (cascade should remove lines too)
    var afterVoid = readModelRepo.findInvoiceById(UUID.fromString(voidInvoiceId), ORG_ID);
    assertThat(afterVoid).isEmpty();

    var lines = readModelRepo.findInvoiceLinesByInvoice(UUID.fromString(voidInvoiceId));
    assertThat(lines).isEmpty();
  }

  private void transitionCustomerToActive(String customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    TestChecklistHelper.transitionToActive(
        mockMvc, customerId, TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner"));
  }

  private void linkProjectToCustomer(String customerId, String projectId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner")))
        .andExpect(status().isCreated());
  }

  private String createInvoice(String customerId, String currency) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerId": "%s", "currency": "%s"}
                        """
                            .formatted(customerId, currency)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
  }

  private void fillPrerequisiteFields(String customerId, String name, String email)
      throws Exception {
    mockMvc
        .perform(
            put("/api/customers/{id}", customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "%s", "email": "%s", "customFields": {"address_line1": "123 Test St", "city": "Test City", "country": "ZA", "tax_number": "VAT123"}}
                    """
                        .formatted(name, email)))
        .andExpect(status().isOk());
  }

  private void addLineItem(
      String invoiceId, String description, String quantity, String unitPrice, int sortOrder)
      throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description": "%s", "quantity": %s, "unitPrice": %s, "sortOrder": %d}
                    """
                        .formatted(description, quantity, unitPrice, sortOrder)))
        .andExpect(status().isCreated());
  }
}
