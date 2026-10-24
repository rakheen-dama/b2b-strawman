package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.payment.PaymentStatus;
import io.b2mash.b2b.b2bstrawman.integration.payment.WebhookResult;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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

/**
 * Real-path pay-now at the balance due (Phase 88 622C, ADR-347): the mock PSP is wired as the
 * tenant's PAYMENT adapter, so the checkout link, the manual part payment, the regenerated link and
 * the webhook all run through the production services. A manual part payment cancels the full-total
 * session; {@code refresh-payment-link} on the PARTIALLY_PAID fee note asks the PSP for the balance
 * due; the webhook posts that session amount as CARD and is idempotent on {@code session_id}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CardPaymentBalanceLinkIntegrationTest {

  private static final String ORG_ID = "org_card_balance_link_test";
  private static final String OWNER = "user_card_link_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private IntegrationRegistry integrationRegistry;
  @Autowired private PaymentReconciliationService reconciliationService;
  @Autowired private InvoiceService invoiceService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Card Balance Link Test Org", null);
    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "card_link_owner@test.com", "CardLink Owner", "owner"));
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
                      var customer =
                          customerRepository.save(
                              TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                                  "CardLink Test Corp", "cardlink@test.com", memberIdOwner));
                      customerId = customer.getId();
                      var project =
                          projectRepository.save(
                              new Project(
                                  "CardLink Test Project", "Pay-now balance link", memberIdOwner));
                      projectId = project.getId();
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      // Wire the mock PSP exactly as MockPaymentIntegrationSeeder does
                      var integration =
                          orgIntegrationRepository
                              .findByDomain(IntegrationDomain.PAYMENT)
                              .orElseGet(
                                  () -> new OrgIntegration(IntegrationDomain.PAYMENT, "mock"));
                      integration.updateProvider("mock", "{}");
                      integration.enable();
                      orgIntegrationRepository.save(integration);
                    }));
    integrationRegistry.evict(tenantSchema, IntegrationDomain.PAYMENT);
  }

  @Test
  void partPaidFeeNote_regeneratesLinkForBalanceDue_andWebhookSettlesIt() throws Exception {
    String invoiceId = createAndSendInvoice();

    Invoice sent = inTenant(() -> invoice(invoiceId));
    BigDecimal total = sent.getTotal();
    String firstSessionId = sent.getPaymentSessionId();
    assertThat(firstSessionId).as("send created a mock checkout session").isNotNull();
    assertThat(sent.getPaymentUrl()).contains("amount=" + total.toPlainString());

    // Manual EFT part payment → PARTIALLY_PAID; the full-total session is cancelled
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 500.00, "paymentMethod": "EFT", "paymentReference": "EFT-1"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));

    BigDecimal balance = total.subtract(new BigDecimal("500.00"));
    inTenant(
        () -> {
          var invoice = invoice(invoiceId);
          assertThat(invoice.getPaymentSessionId()).isNull();
          assertThat(invoice.getPaymentUrl()).isNull();
          assertThat(
                  paymentEventRepository.findBySessionIdAndStatus(
                      firstSessionId, PaymentEventStatus.CANCELLED))
              .isPresent();
          return null;
        });

    // Regenerate the pay-now link on the PARTIALLY_PAID fee note → checkout for the balance due
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/refresh-payment-link")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));

    String secondSessionId =
        inTenant(
            () -> {
              var invoice = invoice(invoiceId);
              assertThat(invoice.getPaymentSessionId()).isNotNull().isNotEqualTo(firstSessionId);
              assertThat(invoice.getPaymentUrl()).contains("amount=" + balance.toPlainString());
              var created =
                  paymentEventRepository
                      .findBySessionIdAndStatus(
                          invoice.getPaymentSessionId(), PaymentEventStatus.CREATED)
                      .orElseThrow();
              assertThat(created.getAmount()).isEqualByComparingTo(balance);
              return invoice.getPaymentSessionId();
            });

    // The PSP confirms the balance-due checkout; a replay of the same webhook posts nothing more
    var result =
        new WebhookResult(
            true,
            "checkout.completed",
            secondSessionId,
            "CARD-REF-1",
            PaymentStatus.COMPLETED,
            Map.of("invoiceId", invoiceId));
    inTenant(
        () -> {
          reconciliationService.processWebhookResult(result, "mock");
          return null;
        });
    inTenant(
        () -> {
          reconciliationService.processWebhookResult(result, "mock");
          return null;
        });

    inTenant(
        () -> {
          var invoice = invoice(invoiceId);
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
          assertThat(invoice.getAmountPaid()).isEqualByComparingTo(total);
          assertThat(invoice.getPaidAt()).isNotNull();

          List<PaymentEvent> cardEvents =
              paymentEventRepository
                  .findByInvoiceIdAndStatus(invoice.getId(), PaymentEventStatus.COMPLETED)
                  .stream()
                  .filter(e -> secondSessionId.equals(e.getSessionId()))
                  .toList();
          assertThat(
                  paymentEventRepository.findBySessionIdAndStatus(
                      secondSessionId, PaymentEventStatus.CANCELLED))
              .as("the card webhook settles its own session — it does not cancel it")
              .isEmpty();
          assertThat(cardEvents)
              .as("one COMPLETED event for the session despite the replay")
              .singleElement()
              .satisfies(
                  e -> {
                    assertThat(e.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
                    assertThat(e.getProviderSlug()).isEqualTo("mock");
                    assertThat(e.getAmount()).isEqualByComparingTo(balance);
                  });
          return null;
        });
  }

  /**
   * A trust fee transfer that leaves a balance changes what is owed, so the live checkout (created
   * for the old balance) is cancelled — paying it would over-collect. The firm regenerates via
   * refresh-payment-link (OQ-6: no auto-regeneration).
   */
  @Test
  void trustTransferPartPayment_cancelsTheLiveCheckoutSession() throws Exception {
    assertExternalPartPaymentCancelsSession(
        PaymentPosting.trustTransfer(new BigDecimal("400.00"), "TRUST-REF-1"));
  }

  /** As above for a part payment pulled from Xero. */
  @Test
  void xeroPartPayment_cancelsTheLiveCheckoutSession() throws Exception {
    assertExternalPartPaymentCancelsSession(
        PaymentPosting.xero(new BigDecimal("400.00"), "XERO-PAY-1"));
  }

  private void assertExternalPartPaymentCancelsSession(PaymentPosting posting) throws Exception {
    String invoiceId = createAndSendInvoice();
    String sessionId = inTenant(() -> invoice(invoiceId).getPaymentSessionId());
    assertThat(sessionId).as("send created a mock checkout session").isNotNull();

    inTenant(() -> invoiceService.postPayment(UUID.fromString(invoiceId), posting));

    inTenant(
        () -> {
          var invoice = invoice(invoiceId);
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
          assertThat(invoice.getPaymentSessionId()).isNull();
          assertThat(invoice.getPaymentUrl()).isNull();
          assertThat(
                  paymentEventRepository.findBySessionIdAndStatus(
                      sessionId, PaymentEventStatus.CANCELLED))
              .isPresent();
          assertThat(
                  paymentEventRepository.findBySessionIdAndStatus(
                      sessionId, PaymentEventStatus.CREATED))
              .isEmpty();
          return null;
        });
  }

  // --- helpers ---

  private Invoice invoice(String invoiceId) {
    return invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow();
  }

  /** Runs {@code action} in one tenant transaction (no member bound — webhook-like context). */
  private <T> T inTenant(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> action.get()));
  }

  private String createAndSendInvoice() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clientId": "%s", "currency": "ZAR"}
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    String invoiceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description": "Consultation", "quantity": 1, "unitPrice": 1000.00,
                     "sortOrder": 0, "matterId": "%s"}
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());
    return invoiceId;
  }
}
