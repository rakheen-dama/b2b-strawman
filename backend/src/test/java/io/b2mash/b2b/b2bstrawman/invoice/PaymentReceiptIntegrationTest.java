package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.InvoicePartiallyPaidEvent;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F32 (Phase 89 632B, ADR-364): every COMPLETED payment on a legal-za fee note gets a payment
 * receipt — a {@code payment-receipt-za} generated document linked from the payment event, with a
 * stored, never-reused number {@code {invoiceNumber}/R{seq}}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentReceiptIntegrationTest {

  private static final String ORG_ID = "org_payment_receipt_test";
  private static final String OWNER = "user_receipt_owner";
  private static final String ADMIN = "user_receipt_admin";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceTransitionService invoiceTransitionService;
  @Autowired private PaymentReceiptService paymentReceiptService;
  @Autowired private PaymentReceiptListener paymentReceiptListener;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private GeneratedDocumentService generatedDocumentService;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private StorageService storageService;
  @Autowired private AuditService auditService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdAdmin;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Receipt Test Law Firm", "legal-za");
    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "receipt_owner@test.com", "Receipt Owner", "owner"));
    memberIdAdmin =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, ADMIN, "receipt_admin@test.com", "Receipt Admin", "admin"));
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
                                  "Receipt Client", "receipt_client@test.com", memberIdOwner));
                      customerId = customer.getId();
                      var project =
                          projectRepository.save(
                              new Project("Receipt Matter", "Matter for receipts", memberIdOwner));
                      projectId = project.getId();
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));
                    }));
  }

  /** Red at HEAD: no receipt column / no receipt generated for a part payment. */
  @Test
  void partPaymentGeneratesReceiptR1LinkedFromThePaymentEvent() throws Exception {
    String invoiceId = createSentInvoice();
    String invoiceNumber = invoiceNumber(invoiceId);

    var paid =
        postPayment(invoiceId, OWNER, "1000.00", "EFT", "FNB-1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
            .andReturn();
    String eventId = JsonPath.read(paid.getResponse().getContentAsString(), "$.payments[0].id");

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT receipt_generated_document_id, receipt_number FROM \"%s\".payment_events WHERE id = ?::uuid"
                .formatted(tenantSchema),
            eventId);
    assertThat(row.get("receipt_generated_document_id")).isNotNull();
    assertThat(row.get("receipt_number")).isEqualTo(invoiceNumber + "/R1");

    Map<String, Object> doc =
        jdbcTemplate.queryForMap(
            """
            SELECT gd.primary_entity_type, gd.primary_entity_id::text AS entity, gd.generated_by,
                   dt.pack_template_key
            FROM "%s".generated_documents gd
            JOIN "%s".document_templates dt ON dt.id = gd.template_id
            WHERE gd.id = ?
            """
                .formatted(tenantSchema, tenantSchema),
            row.get("receipt_generated_document_id"));
    assertThat(doc.get("pack_template_key")).isEqualTo("payment-receipt-za");
    assertThat(doc.get("primary_entity_type")).isEqualTo("INVOICE");
    assertThat(doc.get("entity")).isEqualTo(invoiceId);
    assertThat(doc.get("generated_by")).isEqualTo(memberIdOwner);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT receipt_sequence FROM \"%s\".invoices WHERE id = ?::uuid"
                    .formatted(tenantSchema),
                Integer.class,
                invoiceId))
        .isEqualTo(1);

    mockMvc
        .perform(
            get("/api/invoices/" + invoiceId + "/payment-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].receiptNumber").value(invoiceNumber + "/R1"))
        .andExpect(
            jsonPath("$[0].receiptGeneratedDocumentId")
                .value(row.get("receipt_generated_document_id").toString()));
  }

  /**
   * Part then full payment: R1 then R2 (stored numbering); the settling (InvoicePaidEvent) receipt
   * is generated by its posting member; the audit row says so.
   */
  @Test
  void partThenFullPayment_numbersR1ThenR2_actorIsThePostingMember() throws Exception {
    String invoiceId = createSentInvoice();
    String invoiceNumber = invoiceNumber(invoiceId);

    UUID first = postAndGetNewestEventId(invoiceId, OWNER, "1000.00", "EFT", "FNB-A");
    var settled =
        postPayment(invoiceId, ADMIN, balanceDue(invoiceId), "CASH", "CASH-B")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andReturn();
    UUID second =
        UUID.fromString(
            JsonPath.read(settled.getResponse().getContentAsString(), "$.payments[0].id"));

    assertThat(receiptNumber(first)).isEqualTo(invoiceNumber + "/R1");
    assertThat(receiptNumber(second)).isEqualTo(invoiceNumber + "/R2");
    var secondDoc = inTenant(() -> generatedDocumentRepository.findById(receiptDoc(second)));
    assertThat(secondDoc).isPresent();
    assertThat(secondDoc.get().getGeneratedBy()).isEqualTo(memberIdAdmin);
    var audit = receiptAudit(UUID.fromString(invoiceId), second);
    assertThat(audit)
        .containsEntry("receipt_number", invoiceNumber + "/R2")
        .containsEntry("actor_source", PaymentReceiptService.ACTOR_POSTING_MEMBER)
        .containsEntry("trigger", "listener")
        .containsEntry("generated_document_id", receiptDoc(second).toString());
  }

  /**
   * G89 632C: two receipts on one fee note on one day used to share the file name {@code <template
   * slug>-<note>-<date>.pdf}; the file name now carries the receipt number (R1, R2).
   */
  @Test
  void twoReceiptsSameNoteSameDay_distinctFilenames() throws Exception {
    String invoiceId = createSentInvoice();
    String noteSlug = invoiceNumber(invoiceId).toLowerCase();

    UUID first = postAndGetNewestEventId(invoiceId, OWNER, "1000.00", "EFT", "FNB-F1");
    UUID second = postAndGetNewestEventId(invoiceId, OWNER, "500.00", "EFT", "FNB-F2");

    String firstName =
        inTenant(() -> generatedDocumentRepository.findById(receiptDoc(first)).orElseThrow())
            .getFileName();
    String secondName =
        inTenant(() -> generatedDocumentRepository.findById(receiptDoc(second)).orElseThrow())
            .getFileName();
    assertThat(firstName).isNotEqualTo(secondName);
    // <template slug>-<fee note>-r<n>-<date>.pdf
    assertThat(firstName).contains("-" + noteSlug + "-r1-").endsWith(".pdf");
    assertThat(secondName).contains("-" + noteSlug + "-r2-").endsWith(".pdf");
  }

  /**
   * Reversal (R6.2): the reversed payment's receipt row and stored object go, the reversal audit
   * row records the number, and the next receipt is R3 — R2 is never reissued.
   */
  @Test
  void reversalDeletesTheReceiptDocument_andTheNextReceiptIsR3() throws Exception {
    String invoiceId = createSentInvoice();
    UUID id = UUID.fromString(invoiceId);
    String invoiceNumber = invoiceNumber(invoiceId);
    postAndGetNewestEventId(invoiceId, OWNER, "1000.00", "EFT", "REV-1");
    UUID second = postAndGetNewestEventId(invoiceId, OWNER, "4000.00", "EFT", "REV-2");
    UUID secondDocId = receiptDoc(second);
    String secondKey =
        inTenant(() -> generatedDocumentRepository.findById(secondDocId).orElseThrow().getS3Key());
    assertThat(storageService.download(secondKey)).isNotEmpty();

    inTenant(
        () -> {
          invoiceTransitionService.reversePayment(id, second);
          return null;
        });

    assertThat(inTenant(() -> generatedDocumentRepository.findById(secondDocId))).isEmpty();
    assertThat(storageService.listKeys(secondKey)).as("stored object deleted").isEmpty();
    var reversal =
        inTenant(
            () ->
                auditService
                    .findEvents(
                        new AuditEventFilter(
                            "invoice",
                            id,
                            null,
                            "invoice.payment_partially_reversed",
                            null,
                            null,
                            null),
                        PageRequest.of(0, 10))
                    .getContent());
    assertThat(reversal).hasSize(1);
    assertThat(reversal.getFirst().getDetails())
        .containsEntry("receipt_number", invoiceNumber + "/R2")
        .containsEntry("payment_event_id", second.toString());

    UUID third = postAndGetNewestEventId(invoiceId, OWNER, "500.00", "EFT", "REV-3");
    assertThat(receiptNumber(third)).isEqualTo(invoiceNumber + "/R3");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT receipt_sequence FROM \"%s\".invoices WHERE id = ?::uuid"
                    .formatted(tenantSchema),
                Integer.class,
                invoiceId))
        .isEqualTo(3);
  }

  /**
   * Lazy path (B11 firm POST, R6.3): a deleted receipt (FK → NULL by the database) is regenerated
   * once with the next number; a second POST returns the same document and creates nothing.
   */
  @Test
  void firmPost_regeneratesADeletedReceiptOnce_withTheNextNumber_andIsIdempotent()
      throws Exception {
    String invoiceId = createSentInvoice();
    String invoiceNumber = invoiceNumber(invoiceId);
    UUID event = postAndGetNewestEventId(invoiceId, OWNER, "1000.00", "EFT", "LAZY-1");
    UUID original = receiptDoc(event);
    inTenant(
        () -> {
          generatedDocumentService.delete(original);
          return null;
        });
    assertThat(receiptDocOrNull(event)).as("FK ON DELETE SET NULL").isNull();
    long docsBefore = invoiceDocCount(invoiceId);

    var first =
        mockMvc
            .perform(
                post("/api/invoices/{id}/payment-events/{pe}/receipt", invoiceId, event)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generatedDocumentId").isNotEmpty())
            .andReturn();
    String regenerated =
        JsonPath.read(first.getResponse().getContentAsString(), "$.generatedDocumentId");
    assertThat(regenerated).isNotEqualTo(original.toString());
    assertThat(receiptDoc(event).toString()).isEqualTo(regenerated);
    assertThat(receiptNumber(event)).isEqualTo(invoiceNumber + "/R2"); // R1 is never reissued

    mockMvc
        .perform(
            post("/api/invoices/{id}/payment-events/{pe}/receipt", invoiceId, event)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generatedDocumentId").value(regenerated));
    assertThat(invoiceDocCount(invoiceId)).isEqualTo(docsBefore + 1);
    assertThat(receiptAudit(UUID.fromString(invoiceId), event))
        .containsEntry("trigger", "lazy")
        .containsEntry("actor_source", PaymentReceiptService.ACTOR_POSTING_MEMBER);
  }

  /** R6.3: the listener path and the lazy path racing on one FK-null event → one receipt. */
  @Test
  void concurrentListenerAndLazyCalls_produceOneReceipt_neverThrow() throws Exception {
    String invoiceId = createSentInvoice();
    UUID id = UUID.fromString(invoiceId);
    UUID event = postAndGetNewestEventId(invoiceId, OWNER, "1000.00", "EFT", "RACE-1");
    UUID original = receiptDoc(event);
    inTenant(
        () -> {
          generatedDocumentService.delete(original);
          return null;
        });
    long docsBefore = invoiceDocCount(invoiceId);

    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<UUID> listenerPath =
          () -> {
            start.await();
            return inTenant(
                () ->
                    paymentReceiptService
                        .generateForPaymentEvent(
                            id, event, memberIdOwner, PaymentReceiptService.ACTOR_POSTING_MEMBER)
                        .orElseThrow());
          };
      Callable<UUID> lazyPath =
          () -> {
            start.await();
            return inTenant(() -> paymentReceiptService.ensureReceipt(id, event, memberIdOwner));
          };
      Future<UUID> a = pool.submit(listenerPath);
      Future<UUID> b = pool.submit(lazyPath);
      start.countDown();
      UUID fromListener = a.get(60, TimeUnit.SECONDS);
      UUID fromLazy = b.get(60, TimeUnit.SECONDS);

      assertThat(fromListener).isEqualTo(fromLazy).isEqualTo(receiptDoc(event));
      assertThat(invoiceDocCount(invoiceId)).isEqualTo(docsBefore + 1);
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * The tenant's receipt template is unavailable (pack row re-keyed — a template with receipts
   * cannot be deleted, D24): the payment still succeeds, the listener WARNs and skips, the FK stays
   * null, and the firm POST is a clear 404 — then generates once the template is back.
   */
  @Test
  void receiptTemplateUnavailable_paymentStands_listenerWarns_lazyPathRecoversAfterRestore()
      throws Exception {
    String invoiceId = createSentInvoice();
    UUID templateId =
        inTenant(
            () ->
                documentTemplateRepository
                    .findPackSeeded("legal-za", "payment-receipt-za")
                    .getFirst()
                    .getId());
    UUID event;
    List<String> warnings;
    try {
      setPackTemplateKey(templateId, "payment-receipt-za-hidden");
      var holder = new UUID[1];
      warnings =
          captureLogs(
              PaymentReceiptService.class,
              Level.WARN,
              () -> {
                try {
                  holder[0] = postAndGetNewestEventId(invoiceId, OWNER, "1000.00", "EFT", "TPL-1");
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
      event = holder[0];
      assertThat(receiptDocOrNull(event)).isNull();
      assertThat(
              inTenant(() -> invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow())
                  .getStatus())
          .isEqualTo(InvoiceStatus.PARTIALLY_PAID);
      assertThat(warnings)
          .anySatisfy(
              m ->
                  assertThat(m)
                      .contains("No payment-receipt-za template")
                      .contains(invoiceId)
                      .contains(event.toString()));

      mockMvc
          .perform(
              post("/api/invoices/{id}/payment-events/{pe}/receipt", invoiceId, event)
                  .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.title").value("Receipt unavailable"))
          .andExpect(
              jsonPath("$.detail")
                  .value(
                      "The payment receipt template (payment-receipt-za) is not installed for"
                          + " this firm."));
    } finally {
      setPackTemplateKey(templateId, "payment-receipt-za");
    }

    mockMvc
        .perform(
            post("/api/invoices/{id}/payment-events/{pe}/receipt", invoiceId, event)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generatedDocumentId").isNotEmpty());
    assertThat(receiptDocOrNull(event)).isNotNull();
  }

  /** Any other generation failure is logged at ERROR with both ids and never thrown. */
  @Test
  void listenerFailure_isLoggedAtErrorWithBothIds_neverThrown() throws Exception {
    String invoiceId = createSentInvoice();
    UUID id = UUID.fromString(invoiceId);
    UUID foreignEvent = UUID.randomUUID(); // not a payment event of this fee note → 404 inside

    List<String> errors =
        captureLogs(
            PaymentReceiptListener.class,
            Level.ERROR,
            () ->
                paymentReceiptListener.onInvoicePartiallyPaid(
                    new InvoicePartiallyPaidEvent(
                        "invoice.partially_paid",
                        "invoice",
                        id,
                        null,
                        memberIdOwner,
                        "Receipt Owner",
                        tenantSchema,
                        ORG_ID,
                        Instant.now(),
                        Map.of("payment_event_id", foreignEvent.toString()),
                        memberIdOwner,
                        invoiceNumber(invoiceId),
                        "Receipt Client",
                        "REF",
                        new BigDecimal("1.00"),
                        new BigDecimal("1.00"),
                        new BigDecimal("4999.00"))));

    assertThat(errors)
        .anySatisfy(
            m ->
                assertThat(m)
                    .contains("Failed to generate payment receipt")
                    .contains(invoiceId)
                    .contains(foreignEvent.toString()));
  }

  /**
   * Real flow (review fix, PR #1728): a genuine render failure during {@code POST
   * /api/invoices/{id}/payment} — the tenant's receipt template body is corrupt (its {@code
   * content} is not a node list), so the real {@code generateDocument} → Tiptap render throws
   * inside the AFTER_COMMIT listener. The payment still returns 200 and stands, the FK stays null,
   * the REQUIRES_NEW rollback leaves the receipt sequence untouched, and the ERROR line names both
   * ids. Once the body is restored the lazy path issues R1 (no number was burned).
   */
  @Test
  void realGenerationFailureDuringPayment_paymentStands_fkNull_errorLoggedWithBothIds()
      throws Exception {
    String invoiceId = createSentInvoice();
    UUID templateId =
        inTenant(
            () ->
                documentTemplateRepository
                    .findPackSeeded("legal-za", "payment-receipt-za")
                    .getFirst()
                    .getId());
    String originalContent =
        jdbcTemplate.queryForObject(
            "SELECT content::text FROM \"%s\".document_templates WHERE id = ?"
                .formatted(tenantSchema),
            String.class,
            templateId);
    UUID event;
    List<String> errors;
    try {
      setTemplateContent(templateId, "{\"type\": \"doc\", \"content\": \"corrupt\"}");
      var holder = new UUID[1];
      errors =
          captureLogs(
              PaymentReceiptListener.class,
              Level.ERROR,
              () -> {
                try {
                  holder[0] = postAndGetNewestEventId(invoiceId, OWNER, "1000.00", "EFT", "FAIL-1");
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
      event = holder[0];
    } finally {
      setTemplateContent(templateId, originalContent);
    }

    assertThat(
            inTenant(() -> invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow())
                .getStatus())
        .isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    assertThat(receiptDocOrNull(event)).isNull();
    assertThat(receiptNumber(event)).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT receipt_sequence FROM \"%s\".invoices WHERE id = ?::uuid"
                    .formatted(tenantSchema),
                Integer.class,
                invoiceId))
        .isZero();
    assertThat(errors)
        .anySatisfy(
            m ->
                assertThat(m)
                    .contains("Failed to generate payment receipt")
                    .contains(invoiceId)
                    .contains(event.toString()));

    mockMvc
        .perform(
            post("/api/invoices/{id}/payment-events/{pe}/receipt", invoiceId, event)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());
    assertThat(receiptNumber(event)).isEqualTo(invoiceNumber(invoiceId) + "/R1");
  }

  /**
   * Provider-posted payment (PSP webhook shape — no member bound, B2): the receipt is generated by
   * the fee note's creator and the audit row says {@code actor_source = invoice_creator}.
   */
  @Test
  void providerPostedPayment_receiptGeneratedByTheInvoiceCreator() throws Exception {
    String invoiceId = createSentInvoice();
    UUID id = UUID.fromString(invoiceId);

    var response =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .call(
                () ->
                    invoiceTransitionService.postPayment(
                        id,
                        PaymentPosting.card(
                            new BigDecimal("700.00"), "payfast", "sess-receipt-1", "PF-1")));
    UUID event = response.payments().getFirst().id();

    UUID creator = inTenant(() -> invoiceRepository.findById(id).orElseThrow().getCreatedBy());
    var doc = inTenant(() -> generatedDocumentRepository.findById(receiptDoc(event)).orElseThrow());
    assertThat(doc.getGeneratedBy()).isEqualTo(creator);
    assertThat(receiptAudit(id, event))
        .containsEntry("actor_source", PaymentReceiptService.ACTOR_INVOICE_CREATOR)
        .containsEntry("generated_by", creator.toString());
  }

  /** Firm POST guards: another fee note's payment event → 404; a non-COMPLETED event → 400. */
  @Test
  void firmPost_foreignPaymentEventIs404_nonCompletedIs400() throws Exception {
    String invoiceA = createSentInvoice();
    String invoiceB = createSentInvoice();
    UUID eventOfA = postAndGetNewestEventId(invoiceA, OWNER, "100.00", "EFT", "GUARD-A");

    mockMvc
        .perform(
            post("/api/invoices/{id}/payment-events/{pe}/receipt", invoiceB, eventOfA)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("PaymentEvent not found"))
        .andExpect(jsonPath("$.detail").value("No paymentevent found with id " + eventOfA));

    UUID pending =
        inTenant(
            () ->
                paymentEventRepository
                    .save(
                        new PaymentEvent(
                            UUID.fromString(invoiceB),
                            "payfast",
                            "sess-pending-1",
                            PaymentEventStatus.CREATED,
                            new BigDecimal("50.00"),
                            "ZAR",
                            "OPERATING"))
                    .getId());
    mockMvc
        .perform(
            post("/api/invoices/{id}/payment-events/{pe}/receipt", invoiceB, pending)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Receipt unavailable"))
        .andExpect(jsonPath("$.detail").value("Only a completed payment has a receipt."));
  }

  // --- helpers ---

  private UUID postAndGetNewestEventId(
      String invoiceId, String subject, String amount, String method, String reference)
      throws Exception {
    var result =
        postPayment(invoiceId, subject, amount, method, reference)
            .andExpect(status().isOk())
            .andReturn();
    // payments[] is newest first
    return UUID.fromString(
        JsonPath.read(result.getResponse().getContentAsString(), "$.payments[0].id"));
  }

  private ResultActions postPayment(
      String invoiceId, String subject, String amount, String method, String reference)
      throws Exception {
    var jwt =
        OWNER.equals(subject)
            ? TestJwtFactory.ownerJwt(ORG_ID, subject)
            : TestJwtFactory.adminJwt(ORG_ID, subject);
    return mockMvc.perform(
        post("/api/invoices/" + invoiceId + "/payment")
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"amount": %s, "paymentMethod": "%s", "paymentReference": "%s"}
                """
                    .formatted(amount, method, reference)));
  }

  private String balanceDue(String invoiceId) {
    return jdbcTemplate
        .queryForObject(
            "SELECT total - amount_paid FROM \"%s\".invoices WHERE id = ?::uuid"
                .formatted(tenantSchema),
            BigDecimal.class,
            invoiceId)
        .toPlainString();
  }

  private String receiptNumber(UUID paymentEventId) {
    return jdbcTemplate.queryForObject(
        "SELECT receipt_number FROM \"%s\".payment_events WHERE id = ?".formatted(tenantSchema),
        String.class,
        paymentEventId);
  }

  private UUID receiptDocOrNull(UUID paymentEventId) {
    return jdbcTemplate.queryForObject(
        "SELECT receipt_generated_document_id FROM \"%s\".payment_events WHERE id = ?"
            .formatted(tenantSchema),
        UUID.class,
        paymentEventId);
  }

  private UUID receiptDoc(UUID paymentEventId) {
    UUID doc = receiptDocOrNull(paymentEventId);
    assertThat(doc).as("receipt for payment event %s", paymentEventId).isNotNull();
    return doc;
  }

  private long invoiceDocCount(String invoiceId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM \"%s\".generated_documents WHERE primary_entity_id = ?::uuid"
            .formatted(tenantSchema),
        Long.class,
        invoiceId);
  }

  /** Details of the newest {@code invoice.receipt_generated} row for one payment event. */
  private Map<String, Object> receiptAudit(UUID invoiceId, UUID paymentEventId) throws Exception {
    var rows =
        inTenant(
            () ->
                auditService
                    .findEvents(
                        new AuditEventFilter(
                            "invoice",
                            invoiceId,
                            null,
                            "invoice.receipt_generated",
                            null,
                            null,
                            null),
                        PageRequest.of(0, 50))
                    .getContent());
    return rows.stream()
        .map(e -> e.getDetails())
        .filter(d -> paymentEventId.toString().equals(d.get("payment_event_id")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no receipt audit row for " + paymentEventId));
  }

  private void setPackTemplateKey(UUID templateId, String key) throws Exception {
    inTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var template = documentTemplateRepository.findById(templateId).orElseThrow();
                  template.setPackTemplateKey(key);
                  return documentTemplateRepository.save(template);
                }));
  }

  private void setTemplateContent(UUID templateId, String contentJson) {
    jdbcTemplate.update(
        "UPDATE \"%s\".document_templates SET content = ?::jsonb WHERE id = ?"
            .formatted(tenantSchema),
        contentJson,
        templateId);
  }

  private static List<String> captureLogs(Class<?> source, Level level, Runnable action) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(source);
    logger.addAppender(appender);
    logger.setLevel(Level.INFO); // test logback config may be quieter than WARN
    try {
      action.run();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(null);
    }
    return appender.list.stream()
        .filter(e -> e.getLevel().isGreaterOrEqual(level))
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private <T> T inTenant(Callable<T> body) throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(body::call);
  }

  private String createSentInvoice() throws Exception {
    var created =
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
    String invoiceId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"matterId": "%s", "description": "Consultation", "quantity": 1.0000,
                     "unitPrice": 5000.00, "sortOrder": 0}
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"overrideWarnings\": true}"))
        .andExpect(status().isOk());
    return invoiceId;
  }

  private String invoiceNumber(String invoiceId) {
    return jdbcTemplate.queryForObject(
        "SELECT invoice_number FROM \"%s\".invoices WHERE id = ?::uuid".formatted(tenantSchema),
        String.class,
        invoiceId);
  }
}
