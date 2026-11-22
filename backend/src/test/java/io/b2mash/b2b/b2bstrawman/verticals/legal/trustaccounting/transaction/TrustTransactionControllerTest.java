package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
class TrustTransactionControllerTest {
  private static final String ORG_ID = "org_trust_txn_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private Clock clock;

  private String tenantSchema;
  private String trustAccountId;
  private String customerId;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Txn Controller Test Org", null)
            .schemaName();

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_txn_ctrl_owner",
                "txn_ctrl_owner@test.com",
                "Txn Ctrl Owner",
                "owner"));
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

    // Enable trust_accounting module (+ disbursements: the excludeLinked test links a payment via
    // the real POST /api/legal/disbursements — Phase 88 Epic 621A)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting", "disbursements"));
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
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 10000.00,
                      "reference": "DEP-CTRL-001",
                      "description": "Controller test deposit",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
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
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 5000.00,
                      "reference": "DEP-CTRL-PAY-SETUP",
                      "description": "Setup deposit for payment test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 2000.00,
                      "reference": "PAY-CTRL-001",
                      "description": "Controller test payment",
                      "transactionDate": "2026-03-02"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
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
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 8000.00,
                      "reference": "DEP-CTRL-XFER-SETUP",
                      "description": "Setup deposit for transfer test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/transfer")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceClientId": "%s",
                      "sourceMatterId": "%s",
                      "targetClientId": "%s",
                      "targetMatterId": "%s",
                      "amount": 3000.00,
                      "reference": "XFER-CTRL-001",
                      "description": "Controller test transfer",
                      "transactionDate": "2026-03-02"
                    }
                    """
                        .formatted(
                            customerId,
                            trustMatterOf(customerId),
                            targetCustomerId,
                            trustMatterOf(targetCustomerId))))
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
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 1000.00,
                      "reference": "DEP-CTRL-LIST",
                      "description": "Deposit for list test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
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
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 6000.00,
                      "reference": "DEP-CTRL-APPROVE-SETUP",
                      "description": "Setup deposit for approve test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
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
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": 1000.00,
                          "reference": "PAY-CTRL-APPROVE",
                          "description": "Payment for approve test",
                          "transactionDate": "2026-03-02"
                        }
                        """
                            .formatted(customerId, trustMatterOf(customerId))))
            .andExpect(status().isCreated())
            .andReturn();

    var paymentId =
        JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id").toString();

    // Owner approves (APPROVE_TRUST_PAYMENT — owner or admin)
    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.id").value(paymentId));
  }

  @Test
  void postApprove_adminRole_returns200WithApprovedStatus() throws Exception {
    // Owner deposits funds
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 7000.00,
                      "reference": "DEP-CTRL-ADMIN-APPROVE-SETUP",
                      "description": "Setup deposit for admin approve test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated());

    // Owner records a payment (will be AWAITING_APPROVAL)
    var paymentResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": 1500.00,
                          "reference": "PAY-CTRL-ADMIN-APPROVE",
                          "description": "Payment for admin approve test",
                          "transactionDate": "2026-03-02"
                        }
                        """
                            .formatted(customerId, trustMatterOf(customerId))))
            .andExpect(status().isCreated())
            .andReturn();

    var paymentId =
        JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id").toString();

    // Admin approves (APPROVE_TRUST_PAYMENT — exercises role→resolveCapabilities→approve path)
    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/approve")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_txn_ctrl_admin")))
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
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 3000.00,
                      "reference": "DEP-CTRL-REJECT-SETUP",
                      "description": "Setup deposit for reject test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
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
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": 500.00,
                          "reference": "PAY-CTRL-REJECT",
                          "description": "Payment for reject test",
                          "transactionDate": "2026-03-02"
                        }
                        """
                            .formatted(customerId, trustMatterOf(customerId))))
            .andExpect(status().isCreated())
            .andReturn();

    var paymentId =
        JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id").toString();

    // Owner rejects (APPROVE_TRUST_PAYMENT — owner or admin)
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
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 4000.00,
                      "reference": "DEP-CTRL-REVERSE",
                      "description": "Deposit for reversal test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
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
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": 1500.00,
                          "reference": "PAY-CTRL-REVERSE",
                          "description": "Payment for reversal test",
                          "transactionDate": "2026-03-02"
                        }
                        """
                            .formatted(customerId, trustMatterOf(customerId))))
            .andExpect(status().isCreated())
            .andReturn();

    var paymentId =
        JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id").toString();

    // Owner approves the payment (APPROVE_TRUST_PAYMENT — owner or admin)
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

  // --- Phase 92 649B: reversalDate (§16.3.11, ruling #6) ---

  @Test
  void postReverse_withoutReversalDate_datesTheContraToday() throws Exception {
    var paymentId = approvedPayment("PAY-CTRL-REV-DEFAULT");

    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/reverse")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_txn_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Payment was made in error\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("REVERSAL"))
        .andExpect(jsonPath("$.status").value("RECORDED"))
        .andExpect(jsonPath("$.reversalOf").value(paymentId))
        .andExpect(jsonPath("$.transactionDate").value(LocalDate.now(clock).toString()));
  }

  @Test
  void postReverse_futureReversalDate_returns400() throws Exception {
    var paymentId = approvedPayment("PAY-CTRL-REV-FUTURE");

    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/reverse")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_txn_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"reason\": \"Payment was made in error\", \"reversalDate\": \"%s\"}"
                        .formatted(LocalDate.now(clock).plusDays(1))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Cannot reverse transaction"))
        .andExpect(jsonPath("$.detail").value("The reversal date cannot be in the future"));

    // Refused before anything was written: the payment is still reversible today.
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions/" + paymentId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.reversedById").doesNotExist());
  }

  /** A R500,00 payment on the customer's matter, funded by a deposit and approved by the owner. */
  private String approvedPayment(String reference) throws Exception {
    var adminJwt = TestJwtFactory.adminJwt(ORG_ID, "user_txn_ctrl_admin");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(adminJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 500.00,
                      "reference": "DEP-%s",
                      "description": "Deposit for a reversal-date test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId), reference)))
        .andExpect(status().isCreated());
    var paymentResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": 500.00,
                          "reference": "%s",
                          "description": "Payment for a reversal-date test",
                          "transactionDate": "2026-03-02"
                        }
                        """
                            .formatted(customerId, trustMatterOf(customerId), reference)))
            .andExpect(status().isCreated())
            .andReturn();
    var paymentId =
        JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.id").toString();
    approve(paymentId);
    return paymentId;
  }

  // --- Phase 88 Epic 621A: list filters honoured (matterId / type / status / excludeLinked) ---

  @Test
  void getTransactions_withMatterTypeStatusFilters_returnsOnlyMatchingRows() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner");
    var clientId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Trust Filter Client", "trust_filter_client@test.com");
    var matterA = clientMatter(clientId, "Trust Filter Matter A");
    var matterB = clientMatter(clientId, "Trust Filter Matter B");

    var depositA = recordDeposit(clientId, matterA, "5000.00", "DEP-FILTER-A");
    var approvedA = recordPayment(clientId, matterA, "1000.00", "PAY-FILTER-A1");
    approve(approvedA);
    var pendingA = recordPayment(clientId, matterA, "500.00", "PAY-FILTER-A2"); // AWAITING_APPROVAL
    // G89-F12: a payment on matter B needs the client's money on matter B
    recordDeposit(clientId, matterB, "1000.00", "DEP-FILTER-B");
    var approvedB = recordPayment(clientId, matterB, "250.00", "PAY-FILTER-B1");
    approve(approvedB);

    // matterId + type + status → only the approved PAYMENT on matter A.
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .param("matterId", matterA)
                .param("type", "PAYMENT")
                .param("status", "APPROVED")
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(approvedA))
        .andExpect(jsonPath("$.page.totalElements").value(1));

    // matterId alone → all three matter-A rows, nothing from matter B.
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .param("matterId", matterA)
                .param("size", "50")
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(depositA, approvedA, pendingA)));

    // type alone (account-wide, shared state) → every row is a PAYMENT.
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .param("type", "PAYMENT")
                .param("size", "200")
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].transactionType", everyItem(is("PAYMENT"))));
  }

  @Test
  void getTransactions_excludeLinked_hidesPaymentAlreadyLinkedToDisbursement() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner");
    var clientId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Trust Link Client", "trust_link_client@test.com");
    var matter = clientMatter(clientId, "Trust Link Matter");

    recordDeposit(clientId, matter, "5000.00", "DEP-LINK-1");
    var linked = recordPayment(clientId, matter, "1250.00", "PAY-LINK-DEEDS");
    approve(linked);
    var free = recordPayment(clientId, matter, "750.00", "PAY-LINK-FREE");
    approve(free);

    // Link `linked` to a trust-funded disbursement through the real endpoint.
    mockMvc
        .perform(
            post("/api/legal/disbursements")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "matterId": "%s",
                      "clientId": "%s",
                      "category": "DEEDS_OFFICE_FEES",
                      "description": "Deeds office lodgement",
                      "amount": 1250.00,
                      "paymentSource": "TRUST_ACCOUNT",
                      "trustTransactionId": "%s",
                      "incurredDate": "2026-03-03",
                      "supplierName": "Deeds Office"
                    }
                    """
                        .formatted(matter, clientId, linked)))
        .andExpect(status().isCreated());

    // excludeLinked=true → only the unused payment.
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .param("matterId", matter)
                .param("type", "PAYMENT")
                .param("status", "APPROVED")
                .param("excludeLinked", "true")
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(free));

    // Default (excludeLinked omitted) is unchanged → both payments.
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .param("matterId", matter)
                .param("type", "PAYMENT")
                .param("status", "APPROVED")
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(linked, free)));
  }

  // --- Phase 92 646E: registry as data on the transaction DTO ---

  @Test
  void getTransactions_rowsCarryTypeLabelDirectionLedger_andFilterByLedger() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner");
    var clientId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Trust Registry Client", "trust_registry_client@test.com");
    var matter = clientMatter(clientId, "Trust Registry Matter");
    var depositId = recordDeposit(clientId, matter, "2000.00", "DEP-646E-A");
    var pendingId = recordPayment(clientId, matter, "300.00", "PAY-646E-A"); // AWAITING_APPROVAL

    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions/" + depositId)
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeLabel").value("Deposit"))
        .andExpect(jsonPath("$.direction").value("IN"))
        .andExpect(jsonPath("$.ledger").value("CLIENT"))
        .andExpect(jsonPath("$.creditorAccountId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.investmentId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.postedAt").isNotEmpty())
        .andExpect(jsonPath("$.reversalPending").value(false));

    // Unposted rows still show the way they will move (D2)
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions/" + pendingId)
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
        .andExpect(jsonPath("$.typeLabel").value("Payment"))
        .andExpect(jsonPath("$.direction").value("OUT"))
        .andExpect(jsonPath("$.postedAt").value(org.hamcrest.Matchers.nullValue()));

    // The list carries the same fields; ?ledger=CLIENT keeps this test's rows, NON_CLIENT drops
    // them
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .param("matterId", matter)
                .param("ledger", "CLIENT")
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(depositId, pendingId)))
        .andExpect(jsonPath("$.content[*].ledger", everyItem(is("CLIENT"))))
        .andExpect(jsonPath("$.page.totalElements").value(2));
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .param("matterId", matter)
                .param("ledger", "NON_CLIENT")
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  void reversals_takeTheOppositeDirection_andAPendingContraIsFlagged() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner");
    var adminJwt = TestJwtFactory.adminJwt(ORG_ID, "user_txn_ctrl_admin");
    var clientId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Trust Reversal Dir Client", "trust_reversal_dir@test.com");
    var matter = clientMatter(clientId, "Trust Reversal Dir Matter");
    var depositId = recordDeposit(clientId, matter, "3000.00", "DEP-646E-R");
    var paymentId = recordPayment(clientId, matter, "400.00", "PAY-646E-R");
    approve(paymentId);

    // A payment's reversal posts at once and puts the money back: IN (F8)
    var paymentContra =
        mockMvc
            .perform(
                post("/api/trust-transactions/" + paymentId + "/reverse")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Paid twice\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transactionType").value("REVERSAL"))
            .andExpect(jsonPath("$.typeLabel").value("Reversal"))
            .andExpect(jsonPath("$.direction").value("IN"))
            .andReturn();
    var paymentContraId =
        JsonPath.read(paymentContra.getResponse().getContentAsString(), "$.id").toString();

    // A deposit's reversal waits for approval and takes the money out: OUT, unposted
    var depositContra =
        mockMvc
            .perform(
                post("/api/trust-transactions/" + depositId + "/reverse")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Cheque bounced\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
            .andExpect(jsonPath("$.direction").value("OUT"))
            .andExpect(jsonPath("$.postedAt").value(org.hamcrest.Matchers.nullValue()))
            .andReturn();
    var depositContraId =
        JsonPath.read(depositContra.getResponse().getContentAsString(), "$.id").toString();

    // R1: the deposit is still RECORDED with no reversedById, but its contra is pending
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions/" + depositId)
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECORDED"))
        .andExpect(jsonPath("$.reversedById").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.reversalPending").value(true));
    // The payment's contra posted: REVERSED with reversedById, nothing pending
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions/" + paymentId)
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVERSED"))
        .andExpect(jsonPath("$.reversedById").value(paymentContraId))
        .andExpect(jsonPath("$.reversalPending").value(false));

    // The list (batched lookups) agrees row for row
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .param("matterId", matter)
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(4)))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].direction", paymentContraId).value("IN"))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].direction", depositContraId).value("OUT"))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].reversalPending", depositId).value(true))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].reversalPending", paymentId).value(false));

    // The client-ledger history (native query) carries the same fields
    var history =
        "/api/trust-accounts/" + trustAccountId + "/client-ledgers/" + clientId + "/history";
    mockMvc
        .perform(get(history).with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(4)))
        .andExpect(
            jsonPath("$.content[?(@.id == '%s')].typeLabel", paymentContraId).value("Reversal"))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].direction", paymentContraId).value("IN"))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].direction", depositContraId).value("OUT"))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].direction", paymentId).value("OUT"))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].typeLabel", depositId).value("Deposit"))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].ledger", depositId).value("CLIENT"))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].reversalPending", depositId).value(true))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].reversalPending", paymentId).value(false))
        .andExpect(jsonPath("$.content[?(@.id == '%s')].postedAt", depositId).isNotEmpty())
        .andExpect(
            jsonPath("$.content[?(@.id == '%s')].postedAt", depositContraId)
                .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
  }

  // --- F-646E-2: date range + client filters on the list endpoint ---

  @Test
  void listTransactions_dateRangeAndClientFilters_narrowTheRows() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner");
    var clientA =
        TestEntityHelper.createCustomer(mockMvc, ownerJwt, "Filter Client A", "f646e2_a@test.com");
    var clientB =
        TestEntityHelper.createCustomer(mockMvc, ownerJwt, "Filter Client B", "f646e2_b@test.com");
    var a1 =
        recordTxn(
            "deposit", clientA, trustMatterOf(clientA).toString(), "100.00", "F2-A1", "2026-04-01");
    var a2 =
        recordTxn(
            "deposit", clientA, trustMatterOf(clientA).toString(), "100.00", "F2-A2", "2026-04-10");
    var b1 =
        recordTxn(
            "deposit", clientB, trustMatterOf(clientB).toString(), "100.00", "F2-B1", "2026-04-10");
    var url = "/api/trust-accounts/" + trustAccountId + "/transactions";

    // client filter: only client A's two rows
    mockMvc
        .perform(get(url).with(ownerJwt).param("clientId", clientA).param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(a1, a2)));

    // date range: only the 2026-04-10 rows (both clients)
    mockMvc
        .perform(
            get(url)
                .with(ownerJwt)
                .param("dateFrom", "2026-04-05")
                .param("dateTo", "2026-04-10")
                .param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(a2, b1)));

    // inclusive bounds + client: dateTo equal to the row's date keeps it; dateFrom after drops it
    mockMvc
        .perform(
            get(url)
                .with(ownerJwt)
                .param("clientId", clientA)
                .param("dateTo", "2026-04-01")
                .param("size", "100"))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(a1)));
    mockMvc
        .perform(
            get(url)
                .with(ownerJwt)
                .param("clientId", clientB)
                .param("dateFrom", "2026-04-11")
                .param("size", "100"))
        .andExpect(jsonPath("$.content", hasSize(0)));

    // an unknown client id is an empty page, not an error
    mockMvc
        .perform(get(url).with(ownerJwt).param("clientId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  void listTransactions_fromAfterTo_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/transactions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner"))
                .param("dateFrom", "2026-05-02")
                .param("dateTo", "2026-05-01"))
        .andExpect(status().isBadRequest());
  }

  private String recordDeposit(String clientId, String matterId, String amount, String reference)
      throws Exception {
    return recordTxn("deposit", clientId, matterId, amount, reference, "2026-03-01");
  }

  private String recordPayment(String clientId, String matterId, String amount, String reference)
      throws Exception {
    return recordTxn("payment", clientId, matterId, amount, reference, "2026-03-02");
  }

  private String recordTxn(
      String kind, String clientId, String matterId, String amount, String reference, String date)
      throws Exception {
    // Recorded by the admin so the owner can approve (a recorder cannot be the sole approver).
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/" + kind)
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_txn_ctrl_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": %s,
                          "reference": "%s",
                          "description": "621A filter test",
                          "transactionDate": "%s"
                        }
                        """
                            .formatted(clientId, matterId, amount, reference, date)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private void approve(String transactionId) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-transactions/" + transactionId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  // --- PR #1752 review: amounts finer than a cent are refused before any balance logic ---

  @Test
  void postTransfer_fractionalCentAmount_returns400InvalidAmount_notInsufficientBalance()
      throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner");
    var sourceId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Fractional Source Client", "fractional_source@test.com");
    var targetId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Fractional Target Client", "fractional_target@test.com");

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 100.00,
                      "reference": "DEP-FRACTION-SETUP",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(sourceId, trustMatterOf(sourceId))))
        .andExpect(status().isCreated());

    // 100.001 against a 100.00 balance: before the fix this was refused as "Insufficient trust
    // balance" with both figures rendered as R 100,00.
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/transfer")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceClientId": "%s",
                      "sourceMatterId": "%s",
                      "targetClientId": "%s",
                      "targetMatterId": "%s",
                      "amount": 100.001,
                      "reference": "XFER-FRACTION-001",
                      "transactionDate": "2026-03-02"
                    }
                    """
                        .formatted(
                            sourceId, trustMatterOf(sourceId), targetId, trustMatterOf(targetId))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid amount"))
        .andExpect(jsonPath("$.detail").value("Amount must not have more than 2 decimal places"));
  }

  @Test
  void postDeposit_fractionalCentAmount_returns400InvalidAmount() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 100.005,
                      "reference": "DEP-FRACTION-001",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid amount"))
        .andExpect(jsonPath("$.detail").value("Amount must not have more than 2 decimal places"));
  }

  @Test
  void postDeposit_trailingZerosBeyondCents_isAccepted() throws Exception {
    // 250.500 is a whole number of cents; only significant sub-cent digits are refused.
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_txn_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 250.500,
                      "reference": "DEP-TRAILING-ZERO-001",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value(250.50));
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

  private final java.util.Map<String, UUID> trustMatters = new java.util.HashMap<>();

  /** G89-F28: a client's trust postings sit on a matter of its own (one per client). */
  private UUID trustMatterOf(String clientId) {
    return trustMatters.computeIfAbsent(
        clientId,
        id ->
            TestTrustMatterHelper.openClientMatter(
                tenantSchema,
                transactionTemplate,
                projectRepository,
                UUID.fromString(id),
                "Trust matter " + id,
                ownerMemberId));
  }

  /** G89-F28: a matter opened for the client (trust postings need the client's own matter). */
  private String clientMatter(String clientId, String name) {
    return TestTrustMatterHelper.openClientMatter(
            tenantSchema,
            transactionTemplate,
            projectRepository,
            UUID.fromString(clientId),
            name,
            ownerMemberId)
        .toString();
  }
}
