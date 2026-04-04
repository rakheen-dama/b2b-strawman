package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustTransactionControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_trust_tx_ctrl";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID trustAccountId;
  private UUID customerId;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Tx Controller Test Org", null)
            .schemaName();

    ownerMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_tx_ctrl_owner", "tx_ctrl_owner@test.com", "Tx Owner", "owner"));
    syncMember(ORG_ID, "user_tx_ctrl_owner2", "tx_ctrl_owner2@test.com", "Tx Owner2", "owner");
    syncMember(ORG_ID, "user_tx_ctrl_admin", "tx_ctrl_admin@test.com", "Tx Admin", "admin");
    syncMember(ORG_ID, "user_tx_ctrl_member", "tx_ctrl_member@test.com", "Tx Member", "member");

    // Enable trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create trust account
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Test Trust Account",
                          "bankName": "Test Bank",
                          "branchCode": "123456",
                          "accountNumber": "98765432101",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-01",
                          "notes": "Test trust account for controller tests"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId =
        UUID.fromString(JsonPath.read(accountResult.getResponse().getContentAsString(), "$.id"));

    // Create a customer in the tenant schema
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          TestCustomerFactory.createActiveCustomer(
                              "Trust Test Client", "trust-client@test.com", ownerMemberId);
                      var saved = customerRepository.save(customer);
                      customerId = saved.getId();
                    }));
  }

  // --- 442.3: Recording tests ---

  @Test
  void postDeposit_returns201WithRecordedStatus() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 15000.00,
                      "reference": "DEP/2026/001",
                      "description": "Litigation deposit",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("DEPOSIT"))
        .andExpect(jsonPath("$.amount").value(15000.00))
        .andExpect(jsonPath("$.status").value("RECORDED"))
        .andExpect(jsonPath("$.customerName").value("Trust Test Client"))
        .andExpect(jsonPath("$.recordedByName").value("Tx Owner"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void postPayment_returns201WithAwaitingApproval() throws Exception {
    // First deposit to fund the client
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 20000.00,
                      "reference": "DEP/2026/PAY-SETUP",
                      "description": "Setup deposit for payment test",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 5000.00,
                      "reference": "PAY/2026/001",
                      "description": "Payment to third party",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("PAYMENT"))
        .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
        .andExpect(jsonPath("$.customerName").value("Trust Test Client"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void postTransfer_returns201WithPairedTransactions() throws Exception {
    // Create a second customer for transfer target
    final UUID[] targetCustomerId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var target =
                          TestCustomerFactory.createActiveCustomer(
                              "Transfer Target", "transfer-target@test.com", ownerMemberId);
                      targetCustomerId[0] = customerRepository.save(target).getId();
                    }));

    // Deposit funds for source customer
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 10000.00,
                      "reference": "DEP/2026/XFER-SETUP",
                      "description": "Setup for transfer test",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/transfer")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceCustomerId": "%s",
                      "targetCustomerId": "%s",
                      "amount": 3000.00,
                      "reference": "XFER/2026/001",
                      "description": "Inter-client transfer",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(customerId, targetCustomerId[0])))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].transactionType").value("TRANSFER_OUT"))
        .andExpect(jsonPath("$[1].transactionType").value("TRANSFER_IN"))
        .andExpect(jsonPath("$[0].amount").value(3000.00))
        .andExpect(jsonPath("$[1].amount").value(3000.00));
  }

  @Test
  void getTransactions_returnsPaginatedList() throws Exception {
    // Deposit to ensure at least one transaction exists
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 1000.00,
                      "reference": "DEP/2026/LIST-TEST",
                      "description": "Deposit for list test",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .with(ownerJwt())
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").isNumber())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  // --- 442.4: Approval tests ---

  @Test
  void postApprove_returns200WithApprovedStatus() throws Exception {
    // Deposit first to fund the customer
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 50000.00,
                      "reference": "DEP/2026/APPROVE-SETUP",
                      "description": "Deposit for approval test",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    // Record a payment (AWAITING_APPROVAL)
    var paymentResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "amount": 2000.00,
                          "reference": "PAY/2026/APPROVE-001",
                          "description": "Payment for approval test",
                          "transactionDate": "2026-04-01"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
            .andReturn();
    String transactionId = JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id");

    // Approve with owner2 (different user than recorder, owner role has APPROVE_TRUST_PAYMENT)
    mockMvc
        .perform(post("/api/trust-transactions/" + transactionId + "/approve").with(owner2Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvedByName").value("Tx Owner2"))
        .andExpect(jsonPath("$.clientBalance").isNumber());
  }

  @Test
  void postReject_returns200WithRejectedStatus() throws Exception {
    // Record a payment (AWAITING_APPROVAL)
    var paymentResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "amount": 1500.00,
                          "reference": "PAY/2026/REJECT-001",
                          "description": "Payment for rejection test",
                          "transactionDate": "2026-04-01"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    String transactionId = JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/trust-transactions/" + transactionId + "/reject")
                .with(owner2Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Insufficient documentation"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReason").value("Insufficient documentation"));
  }

  @Test
  void postReverse_returns201WithReversalTransaction() throws Exception {
    // Deposit to fund the customer
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 30000.00,
                      "reference": "DEP/2026/REVERSE-SETUP",
                      "description": "Deposit for reversal test",
                      "transactionDate": "2026-04-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    // Record and approve a payment
    var paymentResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "amount": 4000.00,
                          "reference": "PAY/2026/REVERSE-001",
                          "description": "Payment to reverse",
                          "transactionDate": "2026-04-01"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    String paymentId = JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id");

    // Approve with owner2 (different from recorder)
    mockMvc
        .perform(post("/api/trust-transactions/" + paymentId + "/approve").with(owner2Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // Reverse
    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/reverse")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Entered in error"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("REVERSAL"))
        .andExpect(jsonPath("$.reversalOf").value(paymentId));
  }

  // --- 442.5: Authorization test ---

  @Test
  void postApprove_memberRole_returns403() throws Exception {
    // Record a payment (AWAITING_APPROVAL) — use owner to create
    var paymentResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "amount": 1000.00,
                          "reference": "PAY/2026/AUTH-001",
                          "description": "Payment for auth test",
                          "transactionDate": "2026-04-01"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    String transactionId = JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id");

    // Attempt to approve with member role — should be forbidden
    mockMvc
        .perform(post("/api/trust-transactions/" + transactionId + "/approve").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tx_ctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor owner2Jwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_tx_ctrl_owner2").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tx_ctrl_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_tx_ctrl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
