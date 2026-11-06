package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F39 -- the portal trust ledger, the home "last trust movement" tile and the deposit email
 * must show the trust transaction's own business date ({@code transaction_date}), not the moment it
 * was recorded, and order rows the way the firm ledger does (transaction date, then recorded time).
 *
 * <p>Drives the real firm deposit path (AFTER_COMMIT portal sync + email) and reads the real portal
 * endpoints. A deposit dated 2026-10-20 is recorded first, then a deposit back-dated to 2026-10-01:
 * the back-dated row must carry 2026-10-01 and sort below the 20 Oct row, whose running balance
 * includes it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalTrustTransactionDateIntegrationTest {

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_portal_trust_txn_date";
  private static final String CONTACT_EMAIL = "lerato-txn-date@test.com";
  private static final LocalDate OCT_01 = LocalDate.of(2026, 10, 1);
  private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionService trustTransactionService;
  @Autowired private TransactionTemplate transactionTemplate;

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Txn Date Firm", "legal-za").schemaName();
    memberId = UUID.fromString(TestMemberHelper.syncOwner(mockMvc, ORG_ID, "portal_txn_date"));

    inTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("trust_accounting"));
                  orgSettingsRepository.save(settings);

                  customerId =
                      customerRepository
                          .saveAndFlush(
                              TestCustomerFactory.createActiveCustomer(
                                  "Lerato Txn Date", CONTACT_EMAIL, memberId))
                          .getId();
                  portalContactService.createContact(
                      ORG_ID,
                      customerId,
                      CONTACT_EMAIL,
                      "Lerato",
                      PortalContact.ContactRole.PRIMARY);

                  trustAccountId =
                      trustAccountRepository
                          .saveAndFlush(
                              new TrustAccount(
                                  "Txn Date Trust",
                                  "Test Bank",
                                  "250655",
                                  "6200-000-DATE",
                                  TrustAccountType.GENERAL,
                                  true,
                                  false,
                                  null,
                                  LocalDate.of(2026, 1, 1),
                                  "Portal transaction-date tests"))
                          .getId();
                }));
  }

  @BeforeEach
  void resetInbox() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  @Test
  void backdatedDeposit_portalRowAndApiCarryTransactionDate_andLedgerOrder() throws Exception {
    UUID matterId = newMatter("Ledger Order Matter");
    UUID oct20 = deposit(matterId, "200.00", "DEP-OCT20", OCT_20);
    UUID oct01 = deposit(matterId, "100.00", "DEP-OCT01", OCT_01); // recorded later, back-dated

    // Matter ledger: newest transaction date first, so the running balance reads down the page
    // (the back-dated deposit was recorded last but sorts below the 20 Oct one).
    String body = portalGet("/portal/trust/matters/" + matterId + "/transactions?page=0&size=50");
    assertThat(JsonPath.<List<String>>read(body, "$.content[*].id"))
        .as("ledger order: transaction date desc, not recorded time")
        .containsExactly(oct20.toString(), oct01.toString());
    assertThat(JsonPath.<List<Number>>read(body, "$.content[*].runningBalance"))
        .extracting(Number::doubleValue)
        .containsExactly(300.0, 100.0);
    assertThat(JsonPath.<List<String>>read(body, "$.content[*].transactionDate"))
        .containsExactly("2026-10-20", "2026-10-01");

    // The portal read-model row carries the business date.
    assertThat(storedTransactionDate(oct01)).isEqualTo(OCT_01);
    assertThat(storedTransactionDate(oct20)).isEqualTo(OCT_20);

    // Home tile movements feed: same date and order.
    String movements = portalGet("/portal/trust/movements?limit=10");
    List<String> movementIds = JsonPath.read(movements, "$[*].id");
    List<String> movementDates = JsonPath.read(movements, "$[*].transactionDate");
    assertThat(movementDates.get(movementIds.indexOf(oct01.toString()))).isEqualTo("2026-10-01");
    assertThat(movementIds.indexOf(oct20.toString()))
        .as("the 20 Oct movement is newer than the back-dated 1 Oct one")
        .isLessThan(movementIds.indexOf(oct01.toString()));
  }

  @Test
  void backdatedDepositEmail_showsTransactionDate() throws Exception {
    UUID matterId = newMatter("Email Date Matter");
    deposit(matterId, "750.00", "DEP-EMAIL-OCT01", OCT_01);

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage message = null;
    for (MimeMessage msg : greenMail.getReceivedMessages()) {
      if (CONTACT_EMAIL.equals(msg.getAllRecipients()[0].toString())
          && msg.getSubject() != null
          && msg.getSubject().toLowerCase().contains("trust account activity")) {
        message = msg;
      }
    }
    assertThat(message).as("trust-activity email for the deposit").isNotNull();
    assertThat(bodyOf(message))
        .as("Date cell must be the deposit's transaction date")
        .contains(">1 Oct 2026<");
  }

  // --- helpers ---

  private UUID newMatter(String name) {
    return inTenantReturning(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var project = new Project(name, "F39", memberId);
                  project.setCustomerId(customerId);
                  return projectRepository.saveAndFlush(project).getId();
                }));
  }

  private UUID deposit(UUID matterId, String amount, String reference, LocalDate date) {
    return inTenantReturning(
        () ->
            transactionTemplate.execute(
                tx ->
                    trustTransactionService
                        .recordDeposit(
                            trustAccountId,
                            new RecordDepositRequest(
                                customerId,
                                matterId,
                                new BigDecimal(amount),
                                reference,
                                "Deposit " + reference,
                                date))
                        .id()));
  }

  private LocalDate storedTransactionDate(UUID transactionId) {
    return portalJdbc
        .sql("SELECT transaction_date FROM portal.portal_trust_transaction WHERE id = ?")
        .params(transactionId)
        .query(LocalDate.class)
        .single();
  }

  private String portalGet(String path) throws Exception {
    return mockMvc
        .perform(
            get(path)
                .header(
                    "Authorization", "Bearer " + portalJwtService.issueToken(customerId, ORG_ID)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void inTenant(Runnable runnable) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(runnable);
  }

  private <T> T inTenantReturning(ScopedValue.CallableOp<T, RuntimeException> op) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(op);
  }

  /** Decoded text of a (possibly multipart) message, so the HTML part's cells are searchable. */
  private static String bodyOf(MimeMessage message) throws Exception {
    Object content = message.getContent();
    if (content instanceof String s) {
      return s;
    }
    StringBuilder out = new StringBuilder();
    if (content instanceof Multipart multipart) {
      collect(multipart, out);
    }
    return out.toString();
  }

  private static void collect(Multipart multipart, StringBuilder out) throws Exception {
    for (int i = 0; i < multipart.getCount(); i++) {
      Object inner = multipart.getBodyPart(i).getContent();
      if (inner instanceof String s) {
        out.append(s);
      } else if (inner instanceof Multipart nested) {
        collect(nested, out);
      }
    }
  }
}
