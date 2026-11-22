package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportRenderingService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 FU-4 (register row "649B W1", founder ruling W1): the firm's client-ledger-statement
 * report is handed to the client ("Generate Statement - ... for this client"), so it applies the
 * "reversal reasons stay off client surfaces" rule ({@code
 * TrustTransactionTypeLabels#clientDescription}) on every output path: rows (execute / preview
 * HTML), PDF and CSV.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientLedgerStatementReversalReasonIntegrationTest {

  private static final String ORG_ID = "org_cls_reversal_reason_test";

  /** The fixture's reversal reason (TrustBooksFixture#reverse) - firm-internal free text. */
  private static final String FIRM_REASON = "trust books fixture: reversed";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private ClientLedgerStatementQuery ledgerStatementQuery;
  @Autowired private ReportRenderingService renderingService;

  private String schema;
  private UUID memberId;
  private String reversalReference;
  private Map<String, Object> params;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Ledger Statement Attorneys", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_cls_owner", "cls_owner@test.com", "CLS Owner", "owner"));
    UUID approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_cls_approver",
                "cls_approver@test.com",
                "CLS Approver",
                "admin"));
    var books = fixture.tenant(schema, ORG_ID, memberId);
    UUID accountId = books.freshAccount(false);
    var client = books.newClient("ledger statement");
    books.deposit(accountId, client, "1000.00");
    var payment = books.requestPayment(accountId, client, "400.00");
    books.approve(accountId, payment, approverId, Expected.cashbookAndClient("-400.00"));
    var reversal = books.reverse(accountId, payment, Expected.cashbookAndClient("400.00"));
    reversalReference = reversal.reference();
    params = new HashMap<>();
    params.put("trust_account_id", accountId);
    params.put("customer_id", client.id());
    params.put("dateFrom", "2026-01-01");
    params.put("dateTo", books.today().toString());
  }

  private <T> T inTenant(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }

  @Test
  void execute_reversalRowReadsGeneratedDescription_neverTheFirmReason() {
    var result = inTenant(() -> ledgerStatementQuery.executeAll(new HashMap<>(params)));
    var reversal = result.rows().stream().filter(r -> "Reversal".equals(r.get("type"))).findFirst();
    assertThat(reversal).isPresent();
    assertThat(reversal.get().get("description")).isEqualTo("Reversal — " + reversalReference);
    assertThat(result.rows())
        .extracting(r -> String.valueOf(r.get("description")))
        .noneMatch(d -> d.contains(FIRM_REASON));
  }

  @Test
  void csvHtmlAndPdf_neverCarryTheFirmReason() {
    var definition = TrustReportPackSeeder.shipped("client-ledger-statement");
    var result = inTenant(() -> ledgerStatementQuery.executeAll(new HashMap<>(params)));

    var csvOut = new ByteArrayOutputStream();
    try {
      renderingService.writeCsv(definition, result, params, csvOut);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    var csv = csvOut.toString(StandardCharsets.UTF_8);
    assertThat(csv).contains("Reversal — " + reversalReference).doesNotContain(FIRM_REASON);

    var html = inTenant(() -> renderingService.renderHtml(definition, result, params));
    assertThat(html).contains("Reversal — " + reversalReference).doesNotContain(FIRM_REASON);

    byte[] pdf = inTenant(() -> renderingService.renderPdf(definition, result, params));
    String text;
    try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
      text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    assertThat(text).contains(reversalReference).doesNotContain(FIRM_REASON);
  }
}
