package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_invoice_test";
  private static final String ORG_ID_B = "org_invoice_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String tenantSchemaB;
  private UUID memberIdOwner;
  private UUID memberIdOwnerB;
  private UUID customerId;
  private UUID customerIdB;

  @BeforeAll
  void setup() throws Exception {
    // --- Tenant A ---
    provisioningService.provisionTenant(ORG_ID, "Invoice Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_invoice_owner", "invoice_owner@test.com", "Invoice Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a customer in tenant A
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          new Customer(
                              "Acme Corp",
                              "acme@test.com",
                              "+1-555-0100",
                              "ACM-001",
                              "Test customer",
                              memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();
                    }));

    // --- Tenant B ---
    provisioningService.provisionTenant(ORG_ID_B, "Invoice Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");

    memberIdOwnerB =
        UUID.fromString(
            syncMember(
                ORG_ID_B,
                "user_invoice_owner_b",
                "invoice_owner_b@test.com",
                "Invoice Owner B",
                "owner"));

    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();

    // Create a customer in tenant B
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .where(RequestScopes.MEMBER_ID, memberIdOwnerB)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          new Customer(
                              "Beta Inc",
                              "beta@test.com",
                              "+1-555-0200",
                              "BET-001",
                              "Test customer B",
                              memberIdOwnerB);
                      customer = customerRepository.save(customer);
                      customerIdB = customer.getId();
                    }));
  }

  @Test
  void shouldSaveAndRetrieveInvoiceInDedicatedSchema() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice =
                      new Invoice(
                          customerId,
                          "USD",
                          "Acme Corp",
                          "acme@test.com",
                          "123 Main St",
                          "Invoice Test Org",
                          memberIdOwner);
                  invoice = invoiceRepository.save(invoice);

                  var found = invoiceRepository.findOneById(invoice.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getCustomerId()).isEqualTo(customerId);
                  assertThat(found.get().getStatus()).isEqualTo(InvoiceStatus.DRAFT);
                  assertThat(found.get().getCurrency()).isEqualTo("USD");
                  assertThat(found.get().getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
                  assertThat(found.get().getTaxAmount()).isEqualByComparingTo(BigDecimal.ZERO);
                  assertThat(found.get().getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
                  assertThat(found.get().getCustomerName()).isEqualTo("Acme Corp");
                  assertThat(found.get().getOrgName()).isEqualTo("Invoice Test Org");
                  assertThat(found.get().getCreatedBy()).isEqualTo(memberIdOwner);
                  assertThat(found.get().getCreatedAt()).isNotNull();
                  assertThat(found.get().getUpdatedAt()).isNotNull();
                }));
  }

  @Test
  void shouldSaveAndRetrieveInvoiceWithTenantFilter() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice =
                      new Invoice(
                          customerId,
                          "EUR",
                          "Acme Corp",
                          "acme@test.com",
                          null,
                          "Invoice Test Org",
                          memberIdOwner);
                  invoice = invoiceRepository.save(invoice);

                  var byCustomer = invoiceRepository.findByCustomerId(customerId);
                  assertThat(byCustomer).isNotEmpty();
                  assertThat(byCustomer).anyMatch(i -> i.getCurrency().equals("EUR"));

                  var byStatus = invoiceRepository.findByStatus(InvoiceStatus.DRAFT);
                  assertThat(byStatus).isNotEmpty();
                }));
  }

  @Test
  void findOneByIdRespectsFilterForCrossTenantIsolation() {
    // Create an invoice in tenant A
    var invoiceIdHolder = new UUID[1];
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice =
                      new Invoice(
                          customerId,
                          "USD",
                          "Acme Corp",
                          "acme@test.com",
                          null,
                          "Invoice Test Org",
                          memberIdOwner);
                  invoice = invoiceRepository.save(invoice);
                  invoiceIdHolder[0] = invoice.getId();
                }));

    // Try to access it from tenant B — should return empty
    runInTenant(
        tenantSchemaB,
        ORG_ID_B,
        memberIdOwnerB,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = invoiceRepository.findOneById(invoiceIdHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void approveTransitionSetsInvoiceNumberAndApprovedBy() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice =
                      new Invoice(
                          customerId,
                          "USD",
                          "Acme Corp",
                          "acme@test.com",
                          null,
                          "Invoice Test Org",
                          memberIdOwner);
                  invoice = invoiceRepository.save(invoice);

                  invoice.approve("INV-0001", memberIdOwner);
                  invoice = invoiceRepository.save(invoice);

                  var found = invoiceRepository.findOneById(invoice.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getStatus()).isEqualTo(InvoiceStatus.APPROVED);
                  assertThat(found.get().getInvoiceNumber()).isEqualTo("INV-0001");
                  assertThat(found.get().getApprovedBy()).isEqualTo(memberIdOwner);
                  assertThat(found.get().getIssueDate()).isEqualTo(LocalDate.now());
                }));
  }

  @Test
  void voidInvoiceFromApprovedSetsStatusToVoid() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice =
                      new Invoice(
                          customerId,
                          "USD",
                          "Acme Corp",
                          "acme@test.com",
                          null,
                          "Invoice Test Org",
                          memberIdOwner);
                  invoice = invoiceRepository.save(invoice);

                  invoice.approve("INV-0002", memberIdOwner);
                  invoice = invoiceRepository.save(invoice);

                  invoice.voidInvoice();
                  invoice = invoiceRepository.save(invoice);

                  var found = invoiceRepository.findOneById(invoice.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getStatus()).isEqualTo(InvoiceStatus.VOID);
                }));
  }

  @Test
  void updateDraftThrowsIllegalStateWhenStatusIsApproved() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice =
                      new Invoice(
                          customerId,
                          "USD",
                          "Acme Corp",
                          "acme@test.com",
                          null,
                          "Invoice Test Org",
                          memberIdOwner);
                  invoice = invoiceRepository.save(invoice);

                  invoice.approve("INV-0003", memberIdOwner);
                  invoice = invoiceRepository.save(invoice);

                  final var approvedInvoice = invoice;
                  assertThatThrownBy(
                          () ->
                              approvedInvoice.updateDraft(
                                  LocalDate.now().plusDays(30),
                                  "Updated notes",
                                  "Net 30",
                                  new BigDecimal("10.00")))
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("Only draft invoices can be edited");
                }));
  }

  // --- Helpers ---

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

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
