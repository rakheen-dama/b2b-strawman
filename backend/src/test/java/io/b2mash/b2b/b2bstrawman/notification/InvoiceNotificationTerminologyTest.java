package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LZKC-031 PR-2 (class D) — stored invoice-notification titles must use the tenant's vertical
 * vocabulary at creation time. Legal-za tenants store "Fee Note … has been sent/paid"; tenants
 * without a vertical profile keep "Invoice …" (identity fallback). Mirrors the LZKC-004/009
 * mechanism: {@code EmailTerminology} resolved from the tenant's {@code OrgSettings} vertical
 * profile (PR #1518), not a blind string replace.
 *
 * <p>Titles are generated once and stored — existing notification rows keep their original copy;
 * only notifications created after this fix pick up the vertical vocabulary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceNotificationTerminologyTest {

  private static final String LEGAL_ORG_ID = "org_lzkc031_notif_legal";
  private static final String GENERIC_ORG_ID = "org_lzkc031_notif_generic";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private NotificationService notificationService;

  private String legalSchema;
  private String genericSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(LEGAL_ORG_ID, "LZKC-031 Law Firm", "legal-za");
    legalSchema = orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).get().getSchemaName();
    TestMemberHelper.syncMember(
        mockMvc,
        LEGAL_ORG_ID,
        "user_lzkc031_notif_legal",
        "lzkc031-notif-legal@test.com",
        "Legal Owner",
        "owner");

    provisioningService.provisionTenant(GENERIC_ORG_ID, "LZKC-031 Generic Org", null);
    genericSchema =
        orgSchemaMappingRepository.findByClerkOrgId(GENERIC_ORG_ID).get().getSchemaName();
    TestMemberHelper.syncMember(
        mockMvc,
        GENERIC_ORG_ID,
        "user_lzkc031_notif_generic",
        "lzkc031-notif-generic@test.com",
        "Generic Owner",
        "owner");
  }

  // actorMemberId is a random UUID (not the synced owner) so the owner stays a recipient —
  // createNotificationsForRecipients excludes the actor.
  private InvoiceSentEvent sentEvent(String orgId, String invoiceNumber, String customerName) {
    return new InvoiceSentEvent(
        "invoice.sent",
        "invoice",
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        "Some Actor",
        null,
        orgId,
        Instant.now(),
        Map.of(),
        null,
        invoiceNumber,
        customerName);
  }

  private InvoicePaidEvent paidEvent(String orgId, String invoiceNumber, String customerName) {
    return new InvoicePaidEvent(
        "invoice.paid",
        "invoice",
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        "Some Actor",
        null,
        orgId,
        Instant.now(),
        Map.of(),
        null,
        invoiceNumber,
        customerName,
        "EFT-LZKC031-001");
  }

  @Test
  void invoiceSent_legalZaTenant_titleUsesFeeNote() {
    var notifications =
        ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
            .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
            .call(
                () ->
                    notificationService.handleInvoiceSent(
                        sentEvent(LEGAL_ORG_ID, "INV-1001", "Dlamini Attorneys")));

    assertThat(notifications).isNotEmpty();
    assertThat(notifications)
        .allSatisfy(
            n ->
                assertThat(n.getTitle())
                    .isEqualTo("Fee Note INV-1001 for Dlamini Attorneys has been sent"));
  }

  @Test
  void invoicePaid_legalZaTenant_titleUsesFeeNote() {
    var notifications =
        ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
            .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
            .call(
                () ->
                    notificationService.handleInvoicePaid(
                        paidEvent(LEGAL_ORG_ID, "INV-1002", "Dlamini Attorneys")));

    assertThat(notifications).isNotEmpty();
    assertThat(notifications)
        .allSatisfy(
            n ->
                assertThat(n.getTitle())
                    .isEqualTo("Fee Note INV-1002 for Dlamini Attorneys has been paid"));
  }

  @Test
  void invoiceSent_genericTenant_titleKeepsInvoice() {
    var notifications =
        ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
            .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
            .call(
                () ->
                    notificationService.handleInvoiceSent(
                        sentEvent(GENERIC_ORG_ID, "INV-2001", "Acme Corp")));

    assertThat(notifications).isNotEmpty();
    assertThat(notifications)
        .allSatisfy(
            n ->
                assertThat(n.getTitle()).isEqualTo("Invoice INV-2001 for Acme Corp has been sent"));
  }

  @Test
  void invoicePaid_genericTenant_titleKeepsInvoice() {
    var notifications =
        ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
            .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
            .call(
                () ->
                    notificationService.handleInvoicePaid(
                        paidEvent(GENERIC_ORG_ID, "INV-2002", "Acme Corp")));

    assertThat(notifications).isNotEmpty();
    assertThat(notifications)
        .allSatisfy(
            n ->
                assertThat(n.getTitle()).isEqualTo("Invoice INV-2002 for Acme Corp has been paid"));
  }
}
