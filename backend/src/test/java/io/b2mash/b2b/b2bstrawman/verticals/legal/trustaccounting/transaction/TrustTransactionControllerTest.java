package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
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
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustTransactionControllerTest {
  private static final String ORG_ID = "org_trust_txn_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String trustAccountId;
  private String customerId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Txn Controller Test Org", null)
            .schemaName();

    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_txn_ctrl_owner",
        "txn_ctrl_owner@test.com",
        "Txn Ctrl Owner",
        "owner");
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_txn_ctrl_admin",
        "txn_ctrl_admin@test.com",
        "Txn Ctrl Admin",
        "admin");
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_txn_ctrl_member",
        "txn_ctrl_member@test.com",
        "Txn Ctrl Member",
        "member");

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

    // Create a trust account
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner");
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Controller Test Trust Account",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000001",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = TestEntityHelper.extractId(accountResult);

    // Create a customer
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Trust Txn Test Customer", "trust_txn_test@test.com");
  }

  // --- 442.3: Controller integration tests -- recording ---

  @Test
  void postDeposit_returns201WithRecordedStatus() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 10000.00,
                      "reference": "DEP-CTRL-001",
                      "description": "Controller test deposit",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("DEPOSIT"))
        .andExpect(jsonPath("$.status").value("RECORDED"))
        .andExpect(jsonPath("$.amount").value(10000.00))
        .andExpect(jsonPath("$.reference").value("DEP-CTRL-001"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void postPayment_returns201WithAwaitingApprovalStatus() throws Exception {
    // First deposit funds so the customer has a balance
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 5000.00,
                      "reference": "DEP-CTRL-PAY-SETUP",
                      "description": "Setup deposit for payment test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 2000.00,
                      "reference": "PAY-CTRL-001",
                      "description": "Controller test payment",
                      "transactionDate": "2026-03-02"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("PAYMENT"))
        .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
        .andExpect(jsonPath("$.amount").value(2000.00))
        .andExpect(jsonPath("$.reference").value("PAY-CTRL-001"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void postTransfer_returns201WithPairedTransactions() throws Exception {
    // Create a second customer for the transfer target
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner");
    var targetCustomerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Transfer Target Customer", "transfer_target@test.com");

    // Deposit funds for the source customer
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 8000.00,
                      "reference": "DEP-CTRL-XFER-SETUP",
                      "description": "Setup deposit for transfer test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/transfer")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceCustomerId": "%s",
                      "targetCustomerId": "%s",
                      "amount": 3000.00,
                      "reference": "XFER-CTRL-001",
                      "description": "Controller test transfer",
                      "transactionDate": "2026-03-02"
                    }
                    """
                        .formatted(customerId, targetCustomerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].transactionType").value("TRANSFER_OUT"))
        .andExpect(jsonPath("$[0].status").value("RECORDED"))
        .andExpect(jsonPath("$[1].transactionType").value("TRANSFER_IN"))
        .andExpect(jsonPath("$[1].status").value("RECORDED"));
  }

  @Test
  void getTransactions_returnsPaginatedList() throws Exception {
    // Record a deposit to ensure at least one transaction exists
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 1000.00,
                      "reference": "DEP-CTRL-LIST",
                      "description": "Deposit for list test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isNotEmpty())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  // --- 442.4: Controller integration tests -- approval ---

  @Test
  void postApprove_returns200WithApprovedStatus() throws Exception {
    // Admin deposits funds (MANAGE_TRUST)
    var adminJwt = TestJwtFactory.adminJwt(ORG_ID, "user_txn_ctrl_admin");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(adminJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 6000.00,
                      "reference": "DEP-CTRL-APPROVE-SETUP",
                      "description": "Setup deposit for approve test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    // Admin records a payment (will be AWAITING_APPROVAL)
    var paymentResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "amount": 1000.00,
                          "reference": "PAY-CTRL-APPROVE",
                          "description": "Payment for approve test",
                          "transactionDate": "2026-03-02"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var paymentId =
        JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id").toString();

    // Owner approves (APPROVE_TRUST_PAYMENT is owner-only)
    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.id").value(paymentId));
  }

  @Test
  void postReject_returns200WithRejectedStatusAndReason() throws Exception {
    // Admin deposits funds (MANAGE_TRUST)
    var adminJwt = TestJwtFactory.adminJwt(ORG_ID, "user_txn_ctrl_admin");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(adminJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 3000.00,
                      "reference": "DEP-CTRL-REJECT-SETUP",
                      "description": "Setup deposit for reject test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    // Admin records a payment
    var paymentResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "amount": 500.00,
                          "reference": "PAY-CTRL-REJECT",
                          "description": "Payment for reject test",
                          "transactionDate": "2026-03-02"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var paymentId =
        JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id").toString();

    // Owner rejects (APPROVE_TRUST_PAYMENT is owner-only)
    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/reject")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "Insufficient documentation"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReason").value("Insufficient documentation"))
        .andExpect(jsonPath("$.id").value(paymentId));
  }

  @Test
  void postReverse_returns201WithReversalTransaction() throws Exception {
    // Admin deposits funds (MANAGE_TRUST)
    var adminJwt = TestJwtFactory.adminJwt(ORG_ID, "user_txn_ctrl_admin");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(adminJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 4000.00,
                      "reference": "DEP-CTRL-REVERSE",
                      "description": "Deposit for reversal test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    // Admin records a payment (MANAGE_TRUST)
    var paymentResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "amount": 1500.00,
                          "reference": "PAY-CTRL-REVERSE",
                          "description": "Payment for reversal test",
                          "transactionDate": "2026-03-02"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var paymentId =
        JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id").toString();

    // Owner approves the payment (APPROVE_TRUST_PAYMENT is owner-only)
    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // Admin reverses the approved payment (MANAGE_TRUST)
    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/reverse")
                .with(adminJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "Payment was made in error"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("REVERSAL"))
        .andExpect(jsonPath("$.reversalOf").value(paymentId))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  // --- 442.5: Controller authorization tests ---

  @Test
  void postApprove_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-transactions/" + "00000000-0000-0000-0000-000000000001" + "/approve")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_txn_ctrl_member")))
        .andExpect(status().isForbidden());
  }
}
