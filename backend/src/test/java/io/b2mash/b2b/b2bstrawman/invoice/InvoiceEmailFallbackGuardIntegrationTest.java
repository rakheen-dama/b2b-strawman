package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LZKC-012 fallback guard (PR #1519 review follow-up).
 *
 * <p>If pack reconciliation failed (or has not run) for a legal-za tenant, the {@code fee-note-za}
 * template is missing. Pre-guard, the invoice-email path silently fell back to the first active
 * generic INVOICE template — the old cover letter — resurrecting the LZKC-012 bug (the client
 * received a cover letter instead of a real fee note). The guard makes {@code
 * GeneratedDocumentService#resolveDefaultInvoiceTemplate} return EMPTY for legal-za in that state,
 * and {@code InvoiceEmailEventListener} then sends the invoice email WITHOUT an attachment (the
 * email still carries the portal link) instead of attaching the wrong document.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceEmailFallbackGuardIntegrationTest {

  // JVM-singleton GreenMail on port 13025 (see GreenMailTestSupport + application-test.yml).
  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_lzkc012_fallback_guard";
  private static final String CUSTOMER_EMAIL = "lzkc012-guard-client@test.com";
  private static final Map<String, Object> COVER_LETTER_CONTENT =
      Map.of(
          "type",
          "doc",
          "content",
          List.of(
              Map.of(
                  "type",
                  "paragraph",
                  "content",
                  List.of(Map.of("type", "text", "text", "Cover letter for {{customer.name}}")))));

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private GeneratedDocumentService generatedDocumentService;
  @Autowired private InvoiceEmailEventListener invoiceEmailEventListener;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID invoiceId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "LZKC-012 Guard Law Firm", "legal-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_lzkc012_guard_owner",
                "lzkc012-guard-owner@test.com",
                "Guard Owner",
                "owner"));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Simulate a tenant where pack reconciliation failed / never delivered the fee
                  // note: the legal-za pack is installed but fee-note-za is absent.
                  documentTemplateRepository
                      .findByPackIdAndPackTemplateKey("legal-za", "fee-note-za")
                      .ifPresent(documentTemplateRepository::delete);

                  // Decoy: an active generic INVOICE-type template (the old cover letter). The
                  // pre-guard fallback chain would pick this and attach the wrong document.
                  documentTemplateRepository.save(
                      new DocumentTemplate(
                          TemplateEntityType.INVOICE,
                          "Fee Note Cover Letter",
                          "fee-note-cover-letter",
                          TemplateCategory.COVER_LETTER,
                          COVER_LETTER_CONTENT));

                  var customer =
                      TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                          "LZKC-012 Guard Client", CUSTOMER_EMAIL, memberId);
                  customer = customerRepository.save(customer);
                  customerId = customer.getId();

                  var invoice =
                      new Invoice(
                          customerId,
                          "ZAR",
                          "LZKC-012 Guard Client",
                          CUSTOMER_EMAIL,
                          null,
                          "LZKC-012 Guard Law Firm",
                          memberId);
                  invoiceId = invoiceRepository.save(invoice).getId();
                }));
  }

  @BeforeEach
  void resetGreenMail() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  @Test
  void resolveDefaultInvoiceTemplate_legalZaWithoutFeeNote_returnsEmpty_neverCoverLetter() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var resolved = generatedDocumentService.resolveDefaultInvoiceTemplate();
                  assertThat(resolved)
                      .as(
                          "legal-za must resolve EMPTY when fee-note-za is unavailable — never the"
                              + " generic cover letter (LZKC-012 guard)")
                      .isEmpty();
                }));
  }

  @Test
  void onInvoiceSent_legalZaWithoutFeeNote_sendsEmailWithoutAttachment_persistsNoDocument()
      throws Exception {
    invoiceEmailEventListener.onInvoiceSent(
        new InvoiceSentEvent(
            "invoice.sent",
            "invoice",
            invoiceId,
            null,
            memberId,
            "Guard Owner",
            tenantSchema,
            ORG_ID,
            Instant.now(),
            Map.of(),
            memberId,
            "INV-GUARD-1",
            "LZKC-012 Guard Client"));

    // The invoice email is still delivered (it carries the portal link)…
    var received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(CUSTOMER_EMAIL);

    // …but WITHOUT a PDF attachment (and in particular not the cover-letter decoy).
    String rawMessage = GreenMailUtil.getWholeMessage(received[0]);
    assertThat(rawMessage)
        .as("email must not carry a PDF attachment when no fee-note template is available")
        .doesNotContain("application/pdf")
        .doesNotContain(".pdf");

    // And no GeneratedDocument was persisted from the cover-letter decoy.
    runInTenant(
        () ->
            assertThat(
                    generatedDocumentRepository
                        .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
                            TemplateEntityType.INVOICE, invoiceId))
                .as("the cover-letter decoy must not be rendered/persisted for the invoice")
                .isEmpty());
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
