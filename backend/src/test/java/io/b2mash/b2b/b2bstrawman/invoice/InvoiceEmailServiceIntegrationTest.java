package io.b2mash.b2b.b2bstrawman.invoice;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryStatus;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceEmailServiceIntegrationTest {

  private static final GreenMail greenMail;

  static {
    greenMail = new GreenMail(ServerSetupTest.SMTP);
    greenMail.start();
  }

  @DynamicPropertySource
  static void configureGreenMail(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", () -> greenMail.getSmtp().getBindTo());
    registry.add("spring.mail.port", () -> String.valueOf(greenMail.getSmtp().getPort()));
    registry.add("spring.mail.username", () -> "");
    registry.add("spring.mail.password", () -> "");
    registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
    registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    registry.add("docteams.email.sender-address", () -> "test@docteams.app");
  }

  private static final String ORG_ID = "org_invoice_email_test";
  private static final String RECIPIENT_EMAIL = "customer@test.com";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private InvoiceEmailService invoiceEmailService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private EmailDeliveryLogRepository deliveryLogRepository;
  @Autowired private EmailRateLimiter rateLimiter;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TemplatePackSeeder templatePackSeeder;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private Invoice savedInvoice;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Invoice Email Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        memberSyncService
            .syncMember(
                ORG_ID, "user_inv_email_alice", RECIPIENT_EMAIL, "Alice Test", null, "owner")
            .memberId();

    // Seed template packs so invoice templates are available
    templatePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);

    // Create customer and invoice
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          createActiveCustomer("Test Customer", RECIPIENT_EMAIL, memberId);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var invoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "Test Customer",
                              RECIPIENT_EMAIL,
                              "123 Test St",
                              "Invoice Email Test Org",
                              memberId);
                      invoice.recalculateTotals(
                          new BigDecimal("1000.00"), false, BigDecimal.ZERO, false);
                      invoice.approve("INV-2026-0001", memberId);
                      invoice.markSent();
                      savedInvoice = invoiceRepository.save(invoice);
                    }));
  }

  @AfterAll
  static void stopGreenMail() {
    greenMail.stop();
  }

  @BeforeEach
  void resetGreenMail() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  @Test
  void sends_email_with_pdf_attachment() throws Exception {
    byte[] pdfBytes = "fake-pdf-content".getBytes();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> invoiceEmailService.sendInvoiceEmail(savedInvoice, pdfBytes));

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getSubject()).contains("INV-2026-0001");
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(RECIPIENT_EMAIL);
  }

  @Test
  void records_delivery_log() {
    byte[] pdfBytes = "fake-pdf-content".getBytes();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> invoiceEmailService.sendInvoiceEmail(savedInvoice, pdfBytes));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var logs = deliveryLogRepository.findAll();
              var relevant =
                  logs.stream()
                      .filter(l -> savedInvoice.getId().equals(l.getReferenceId()))
                      .findFirst();
              assertThat(relevant).isPresent();
              assertThat(relevant.get().getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
              assertThat(relevant.get().getReferenceType()).isEqualTo("INVOICE");
            });
  }

  @Test
  void failure_does_not_throw() {
    greenMail.stop();
    try {
      byte[] pdfBytes = "fake-pdf-content".getBytes();
      assertThatCode(
              () ->
                  ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                      .where(RequestScopes.ORG_ID, ORG_ID)
                      .run(() -> invoiceEmailService.sendInvoiceEmail(savedInvoice, pdfBytes)))
          .doesNotThrowAnyException();
    } finally {
      greenMail.start();
    }
  }

  @Test
  void rate_limited_records_status() {
    // Provision a separate tenant so rate limit exhaustion doesn't affect other tests
    String rlOrgId = "org_inv_email_rl_test";
    String rlTenantSchema =
        provisioningService.provisionTenant(rlOrgId, "Rate Limit Test Org").schemaName();
    planSyncService.syncPlan(rlOrgId, "pro-plan");
    var rlMemberId =
        memberSyncService
            .syncMember(
                rlOrgId, "user_inv_rl_alice", "rl_alice@test.com", "RL Alice", null, "owner")
            .memberId();

    // Create invoice in separate tenant
    final Invoice[] rlInvoice = new Invoice[1];
    ScopedValue.where(RequestScopes.TENANT_ID, rlTenantSchema)
        .where(RequestScopes.ORG_ID, rlOrgId)
        .where(RequestScopes.MEMBER_ID, rlMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          createActiveCustomer("RL Customer", "rl_customer@test.com", rlMemberId);
                      customer = customerRepository.save(customer);
                      var invoice =
                          new Invoice(
                              customer.getId(),
                              "ZAR",
                              "RL Customer",
                              "rl_customer@test.com",
                              "456 Test St",
                              "Rate Limit Test Org",
                              rlMemberId);
                      invoice.recalculateTotals(
                          new BigDecimal("500.00"), false, BigDecimal.ZERO, false);
                      invoice.approve("INV-RL-0001", rlMemberId);
                      invoice.markSent();
                      rlInvoice[0] = invoiceRepository.save(invoice);
                    }));

    byte[] pdfBytes = "fake-pdf-content".getBytes();

    // Exhaust rate limit by sending many emails
    ScopedValue.where(RequestScopes.TENANT_ID, rlTenantSchema)
        .where(RequestScopes.ORG_ID, rlOrgId)
        .run(
            () -> {
              for (int i = 0; i < 51; i++) {
                invoiceEmailService.sendInvoiceEmail(rlInvoice[0], pdfBytes);
              }
            });

    // Check that a RATE_LIMITED log entry exists
    ScopedValue.where(RequestScopes.TENANT_ID, rlTenantSchema)
        .where(RequestScopes.ORG_ID, rlOrgId)
        .run(
            () -> {
              var logs = deliveryLogRepository.findAll();
              var rateLimited =
                  logs.stream()
                      .filter(l -> l.getStatus() == EmailDeliveryStatus.RATE_LIMITED)
                      .filter(l -> rlInvoice[0].getId().equals(l.getReferenceId()))
                      .findFirst();
              assertThat(rateLimited).isPresent();
              assertThat(rateLimited.get().getReferenceType()).isEqualTo("INVOICE");
            });
  }

  @Test
  void attachment_filename_format() throws Exception {
    byte[] pdfBytes = "fake-pdf-content".getBytes();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> invoiceEmailService.sendInvoiceEmail(savedInvoice, pdfBytes));

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);

    // Check attachment filename
    MimeMessage msg = received[0];
    assertThat(msg.getContent()).isInstanceOf(MimeMultipart.class);
    var multipart = (MimeMultipart) msg.getContent();

    boolean foundAttachment = false;
    for (int i = 0; i < multipart.getCount(); i++) {
      var part = multipart.getBodyPart(i);
      if (part.getFileName() != null) {
        assertThat(part.getFileName()).isEqualTo("INV-2026-0001.pdf");
        assertThat(part.getContentType()).contains("application/pdf");
        foundAttachment = true;
      }
    }
    assertThat(foundAttachment).as("PDF attachment should be present").isTrue();
  }
}
