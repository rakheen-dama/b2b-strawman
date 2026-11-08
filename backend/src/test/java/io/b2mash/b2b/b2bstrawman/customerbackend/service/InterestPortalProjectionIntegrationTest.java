package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTrustBalanceView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.mail.Address;
import jakarta.mail.Message;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F22 (HIGH): after an interest run the client's portal trust balance must equal the firm-side
 * client ledger. Before the fix the INTEREST_CREDIT was posted with no matter — the portal projects
 * per matter only, so it never showed — and {@code InterestPosted} was never published, so the
 * portal fan-out listener never ran either.
 *
 * <p>Everything goes through the HTTP API (create → calculate → approve by a second member → post),
 * so the run commits with ORG_ID bound and the AFTER_COMMIT listeners fire as in production. Each
 * test uses its own trust account so runs never overlap; periods are in the past (G89-F11 guard).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterestPortalProjectionIntegrationTest {

  private static final String ORG_ID = "org_interest_portal_projection";
  private static final String OWNER_SUB = "user_ipp_owner";
  private static final String APPROVER_SUB = "user_ipp_approver";

  /** A plain member: never notified of a posted interest run (admins and owners only). */
  private static final String MEMBER_SUB = "user_ipp_member";

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PortalTrustReadModelRepository portalTrustRepo;
  @Autowired private NotificationRepository notificationRepository;

  private String tenantSchema;
  private UUID ownerId;
  private UUID approverId;
  private UUID plainMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Interest Portal Projection Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "ipp_owner@test.com", "IPP Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, APPROVER_SUB, "ipp_approver@test.com", "IPP Approver", "owner"));
    plainMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, MEMBER_SUB, "ipp_member@test.com", "IPP Member", "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  @Test
  void portalTrustBalance_equalsFirmClientLedger_afterInterestRun() throws Exception {
    var clientId = createClient("IPP Client One", "ipp_client_one@test.com");
    var matterId = TestEntityHelper.createProject(mockMvc, ownerJwt(), "IPP Matter One");
    var accountId = createAccountWithRate("62890000101");
    deposit(accountId, clientId, matterId, "100000.00", "2025-01-15", "DEP-IPP-1");

    var runId = postInterestRun(accountId, "2025-03-01", "2025-03-31");

    var cardBalance = cardBalance(accountId, clientId);
    assertThat(cardBalance).isGreaterThan(new BigDecimal("100000.00"));

    var portalTotal =
        portalTrustRepo.findBalancesByCustomer(UUID.fromString(clientId)).stream()
            .map(PortalTrustBalanceView::currentBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(portalTotal).isEqualByComparingTo(cardBalance);

    var portalTxns =
        portalTrustRepo.findTransactions(
            UUID.fromString(clientId), UUID.fromString(matterId), null, null, 50, 0);
    assertThat(portalTxns)
        .anySatisfy(
            t -> {
              assertThat(t.transactionType()).isEqualTo("INTEREST_CREDIT");
              assertThat(t.reference()).startsWith("INT-CREDIT-" + runId);
              assertThat(t.runningBalance()).isEqualByComparingTo(cardBalance);
            });
  }

  @Test
  void postInterestRun_notifiesAdminsNotClients() throws Exception {
    var clientEmail = "ipp_client_two@test.com";
    var clientId = createClient("IPP Client Two", clientEmail);
    var matterId = TestEntityHelper.createProject(mockMvc, ownerJwt(), "IPP Matter Two");
    var accountId = createAccountWithRate("62890000102");
    deposit(accountId, clientId, matterId, "50000.00", "2025-01-15", "DEP-IPP-2");
    greenMail.purgeEmailFromAllMailboxes();

    var runId = postInterestRun(accountId, "2025-04-01", "2025-04-30");

    // Firm admins and owners get the in-app TRUST_INTEREST_POSTED notification for this run
    for (var memberId : List.of(ownerId, approverId)) {
      assertThat(interestPostedNotifications(memberId, runId))
          .as("TRUST_INTEREST_POSTED for member %s", memberId)
          .hasSize(1);
    }
    // ... a plain member does not (PR #1746 review: the "not clients" claim covers staff too)
    assertThat(interestPostedNotifications(plainMemberId, runId))
        .as("TRUST_INTEREST_POSTED for a plain member")
        .isEmpty();

    // ... and the client is never emailed about it
    greenMail.waitForIncomingEmail(2000, 1);
    var toClient =
        Arrays.stream(greenMail.getReceivedMessages())
            .filter(
                m -> {
                  try {
                    Address[] to = m.getRecipients(Message.RecipientType.TO);
                    return to != null
                        && Arrays.stream(to).anyMatch(a -> a.toString().contains(clientEmail));
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                })
            .toList();
    assertThat(toClient).isEmpty();
  }

  // --- Helpers ---

  private List<?> interestPostedNotifications(UUID memberId, UUID runId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx ->
                        notificationRepository
                            .findByRecipientMemberId(memberId, Pageable.unpaged())
                            .getContent()
                            .stream()
                            .filter(n -> "TRUST_INTEREST_POSTED".equals(n.getType()))
                            .filter(n -> runId.equals(n.getReferenceEntityId()))
                            .toList()));
  }

  private String createClient(String name, String email) throws Exception {
    return TestEntityHelper.createCustomer(mockMvc, ownerJwt(), name, email);
  }

  /** A trust account with an LPFF rate (7.5 %, 75 % to the LPFF) effective 2025-01-01. */
  private String createAccountWithRate(String accountNumber) throws Exception {
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "IPP Account %s",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "%s",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2025-01-01"
                        }
                        """
                            .formatted(accountNumber, accountNumber)))
            .andExpect(status().isCreated())
            .andReturn();
    var accountId = TestEntityHelper.extractId(accountResult);

    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/lpff-rates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2025-01-01",
                      "ratePercent": 0.0750,
                      "lpffSharePercent": 0.7500,
                      "notes": "IPP test rate"
                    }
                    """))
        .andExpect(status().isCreated());
    return accountId;
  }

  private void deposit(
      String accountId,
      String clientId,
      String matterId,
      String amount,
      String date,
      String reference)
      throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": %s,
                      "reference": "%s",
                      "description": "Matter deposit",
                      "transactionDate": "%s"
                    }
                    """
                        .formatted(clientId, matterId, amount, reference, date)))
        .andExpect(status().isCreated());
  }

  /** create (owner) → calculate (owner) → approve (second owner) → post (owner); returns run id. */
  private UUID postInterestRun(String accountId, String start, String end) throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + accountId + "/interest-runs")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"periodStart": "%s", "periodEnd": "%s"}
                        """
                            .formatted(start, end)))
            .andExpect(status().isCreated())
            .andReturn();
    var runId = TestEntityHelper.extractId(created);
    mockMvc
        .perform(post("/api/interest-runs/" + runId + "/calculate").with(ownerJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/interest-runs/" + runId + "/approve").with(approverJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/interest-runs/" + runId + "/post").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("POSTED"));
    return UUID.fromString(runId);
  }

  private BigDecimal cardBalance(String accountId, String clientId) throws Exception {
    var body =
        mockMvc
            .perform(
                get("/api/trust-accounts/" + accountId + "/client-ledgers/" + clientId)
                    .with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Object balance = JsonPath.read(body, "$.balance");
    return new BigDecimal(balance.toString());
  }

  private static JwtRequestPostProcessor ownerJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB);
  }

  private static JwtRequestPostProcessor approverJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB);
  }
}
