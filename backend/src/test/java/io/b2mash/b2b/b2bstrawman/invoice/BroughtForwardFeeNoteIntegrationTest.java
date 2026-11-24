package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import io.b2mash.b2b.b2bstrawman.collections.CashDigestData;
import io.b2mash.b2b.b2bstrawman.collections.CashDigestService;
import io.b2mash.b2b.b2bstrawman.collections.CollectionsScanService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.InvoiceSyncEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.event.InvoiceApprovedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncEntryRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncService;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncEntityType;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncTrigger;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnCommitService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDebtorsFiles;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDrainJobHandler;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestDrain;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 93 658A (task 658.6, architecture §17.3.10, ADR-390): the quiet issue of brought-forward
 * fee notes, driven through the take-on write path (upload → cutover date → map → validate → commit
 * through {@link TakeOnTestDrain}). Each fee note is SENT with its source number, dates and one
 * untaxed MANUAL line; nothing of approve or send runs — the firm's number sequence is untouched,
 * no payment link, no {@code InvoiceApprovedEvent} / {@code InvoiceSentEvent}, no notification, no
 * accounting sync entry (with Xero CONNECTED), no automation, no email; only the portal fee note is
 * upserted, without a payment URL. Afterwards they are ordinary SENT fee notes: the cash digest and
 * aging count them, collections no-ops while switched off, a void or payment fires the ordinary
 * listeners — except that a brought-forward fee note is never enqueued for Xero (FD-28) — and
 * {@code INVOICE_GENERATION} is still enforced on {@code createDraft} (FD-21). Every assertion is
 * scoped to this test's own fee notes (id-scope).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
class BroughtForwardFeeNoteIntegrationTest {

  private static final String ORG_ID = "org_bf_fee_note_test";
  private static final String OWNER = "user_bfn_owner";
  private static final String ADMIN = "user_bfn_admin";
  private static final String THANDI_EMAIL = "bf.thandi@example.test";
  private static final String BARE_EMAIL = "bf.bare@example.test";
  private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  /** Relative to today so the aging buckets hold whatever day the suite runs. */
  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));

  private static final LocalDate CUTOVER = TODAY.minusDays(1);

  private static final BigDecimal CUR_AMOUNT = new BigDecimal("1500.00");
  private static final BigDecimal OLD_AMOUNT = new BigDecimal("2750.50");
  private static final BigDecimal MAT_AMOUNT = new BigDecimal("800.00");
  private static final BigDecimal VOID_AMOUNT = new BigDecimal("400.00");
  private static final BigDecimal PAY_AMOUNT = new BigDecimal("1000.00");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private CustomerRepository customers;
  @Autowired private InvoiceRepository invoices;
  @Autowired private InvoiceLineRepository lines;
  @Autowired private PaymentEventRepository paymentEvents;
  @Autowired private AccountingSyncEntryRepository syncEntries;
  @Autowired private AccountingSyncService accountingSync;
  @Autowired private OrgIntegrationRepository orgIntegrations;
  @Autowired private AccountingXeroConnectionRepository xeroConnections;
  @Autowired private AutomationRuleRepository automationRules;
  @Autowired private PortalReadModelRepository portalReadModel;
  @Autowired private CollectionsScanService collectionsScan;
  @Autowired private CashDigestService cashDigest;
  @Autowired private ApplicationEvents events;

  private String schema;
  private UUID ownerId;
  private UUID thandi; // Kazi-ready (address + email)
  private UUID bare; // email only, no billing address — fails INVOICE_GENERATION
  private UUID matter;
  private UUID batchId;
  private Map<String, UUID> feeNotes; // source number → invoice id
  private List<Long> counterBefore;
  private List<Long> counterAfter;
  private CashDigestData digestBefore;
  private CashDigestData digestAfter;
  private int mailBefore;
  private int mailAfter;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Brought Forward Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "bfn_owner@test.com", "BF Owner", "owner"));
    // a second member: void notifications go to owners/admins other than the actor
    TestMemberHelper.syncMember(mockMvc, ORG_ID, ADMIN, "bfn_admin@test.com", "BF Admin", "admin");
    thandi = inTenant(() -> customers.save(kaziReady("Thandi Nkosi", THANDI_EMAIL)).getId());
    bare =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Bare Client", BARE_EMAIL, ownerId))
                    .getId());
    matter = createMatter("Estate late M Nkosi", thandi, "BF-M-1");
    inTenant(
        () -> {
          createConnectedXeroConnection();
          automationRules.save(
              new AutomationRule(
                  "BF test",
                  null,
                  TriggerType.INVOICE_STATUS_CHANGED,
                  Map.of(),
                  List.of(),
                  RuleSource.CUSTOM,
                  null,
                  ownerId));
          return null;
        });

    counterBefore = counter();
    digestBefore = inTenant(cashDigest::assembleData);
    mailBefore = GreenMailTestSupport.getInstance().getReceivedMessages().length;
    batchId =
        commitDebtors(
            TakeOnDebtorsFiles.row(THANDI_EMAIL, "BF-CUR")
                .issue(DMY.format(TODAY.minusDays(20)))
                .due(DMY.format(TODAY.plusDays(30)))
                .amount("1500.00")
                .description("Fees for advice on the estate")
                .ref("BF-CUR"),
            TakeOnDebtorsFiles.row(BARE_EMAIL, "BF-OLD")
                .issue(DMY.format(TODAY.minusDays(130)))
                .due(DMY.format(TODAY.minusDays(100)))
                .amount("2750.50")
                .ref("BF-OLD"),
            TakeOnDebtorsFiles.row(THANDI_EMAIL, "BF-MAT")
                .matter("BF-M-1")
                .issue(DMY.format(TODAY.minusDays(40)))
                .due(DMY.format(TODAY.minusDays(10)))
                .amount("800.00")
                .ref("BF-MAT"),
            TakeOnDebtorsFiles.row(THANDI_EMAIL, "BF-VOID")
                .issue(DMY.format(TODAY.minusDays(30)))
                .due(DMY.format(TODAY.plusDays(10)))
                .amount("400.00")
                .ref("BF-VOID"),
            TakeOnDebtorsFiles.row(THANDI_EMAIL, "BF-PAY")
                .issue(DMY.format(TODAY.minusDays(30)))
                .due(DMY.format(TODAY.plusDays(10)))
                .amount("1000.00")
                .ref("BF-PAY"));
    mailAfter = GreenMailTestSupport.getInstance().getReceivedMessages().length;
    digestAfter = inTenant(cashDigest::assembleData);
    counterAfter = counter();
    feeNotes = new HashMap<>();
    for (String number : List.of("BF-CUR", "BF-OLD", "BF-MAT", "BF-VOID", "BF-PAY")) {
      feeNotes.put(
          number,
          jdbc.queryForObject(
              "SELECT id FROM \"%s\".invoices WHERE invoice_number = ?".formatted(schema),
              UUID.class,
              number));
    }
  }

  // 1
  @Test
  void eachRow_isOneSentFeeNote_withTheSourceNumberAndDates_theBatch_andTheCommitterAsApprover() {
    var expected =
        Map.of(
            "BF-CUR", new LocalDate[] {TODAY.minusDays(20), TODAY.plusDays(30)},
            "BF-OLD", new LocalDate[] {TODAY.minusDays(130), TODAY.minusDays(100)},
            "BF-MAT", new LocalDate[] {TODAY.minusDays(40), TODAY.minusDays(10)});
    for (var e : expected.entrySet()) {
      var invoice = invoice(e.getKey());
      assertThat(invoice.getStatus()).as(e.getKey()).isEqualTo(InvoiceStatus.SENT);
      assertThat(invoice.getInvoiceNumber()).isEqualTo(e.getKey());
      assertThat(invoice.getIssueDate()).isEqualTo(e.getValue()[0]);
      assertThat(invoice.getDueDate()).isEqualTo(e.getValue()[1]);
      assertThat(invoice.getTakeOnBatchId()).isEqualTo(batchId);
      assertThat(invoice.getApprovedBy()).isEqualTo(ownerId);
      assertThat(invoice.getCreatedBy()).isEqualTo(ownerId);
      assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
      assertThat(invoice.getCurrency()).isEqualTo("ZAR");
    }
    assertThat(invoice("BF-CUR").getCustomerId()).isEqualTo(thandi);
    assertThat(invoice("BF-CUR").getCustomerName()).isEqualTo("Thandi Nkosi");
    assertThat(invoice("BF-CUR").getNotes()).isEqualTo("Fees for advice on the estate");
    assertThat(invoice("BF-OLD").getCustomerId()).isEqualTo(bare);
  }

  // 2
  @Test
  void eachFeeNote_hasExactlyOneUntaxedManualLine_andTheTotalIsTheOutstandingAmount() {
    String asAt = BroughtForwardFeeNoteService.AS_AT.format(CUTOVER);
    var amounts = Map.of("BF-CUR", CUR_AMOUNT, "BF-OLD", OLD_AMOUNT, "BF-MAT", MAT_AMOUNT);
    for (var e : amounts.entrySet()) {
      var invoice = invoice(e.getKey());
      var feeNoteLines = inTenant(() -> lines.findByInvoiceIdOrderBySortOrder(invoice.getId()));
      assertThat(feeNoteLines).as(e.getKey()).hasSize(1);
      var line = feeNoteLines.getFirst();
      assertThat(line.getLineType()).isEqualTo(InvoiceLineType.MANUAL);
      assertThat(line.getDescription())
          .isEqualTo("Balance brought forward as at " + asAt + " — fee note " + e.getKey());
      assertThat(line.getQuantity()).isEqualByComparingTo("1");
      assertThat(line.getUnitPrice()).isEqualByComparingTo(e.getValue());
      assertThat(line.getTaxRateId()).isNull();
      assertThat(line.getTaxAmount() == null ? BigDecimal.ZERO : line.getTaxAmount())
          .isEqualByComparingTo("0");
      assertThat(invoice.getTaxAmount()).isEqualByComparingTo("0");
      assertThat(invoice.getSubtotal()).isEqualByComparingTo(e.getValue());
      assertThat(invoice.getTotal()).isEqualByComparingTo(e.getValue());
      assertThat(line.getProjectId()).isEqualTo(e.getKey().equals("BF-MAT") ? matter : null);
    }
  }

  // 3
  @Test
  void theFirmsNumberSequence_isUntouched_andTheNextOrdinaryFeeNoteGetsTheNextNumber()
      throws Exception {
    assertThat(counterAfter).isEqualTo(counterBefore);
    var before = counter();
    String number = approvedOrdinaryFeeNote(thandi).number();
    var after = counter();
    long expectedNext = before.isEmpty() ? 2 : before.getFirst() + 1;
    assertThat(after).containsExactly(expectedNext);
    assertThat(number).isEqualTo("INV-%04d".formatted(expectedNext - 1));
  }

  // 4
  @Test
  void noPaymentLink_andNoPaymentEvent() {
    for (String number : List.of("BF-CUR", "BF-OLD", "BF-MAT")) {
      var invoice = invoice(number);
      assertThat(invoice.getPaymentUrl()).as(number).isNull();
      assertThat(invoice.getPaymentSessionId()).isNull();
      assertThat(inTenant(() -> paymentEvents.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId())))
          .isEmpty();
    }
  }

  // 5 + 9: events captured around a commit made inside this test
  @Test
  void aCommit_publishesOnlyThePortalSync_andSendsNoEmail() throws Exception {
    int mail = GreenMailTestSupport.getInstance().getReceivedMessages().length;
    commitDebtors(
        TakeOnDebtorsFiles.row(THANDI_EMAIL, "BF-EVT")
            .issue(DMY.format(TODAY.minusDays(15)))
            .due(DMY.format(TODAY.plusDays(15)))
            .amount("321.00")
            .ref("BF-EVT"));
    UUID id = invoice("BF-EVT").getId();
    assertThat(events.stream(InvoiceSentEvent.class).filter(e -> id.equals(e.entityId())))
        .isEmpty();
    assertThat(events.stream(InvoiceApprovedEvent.class).filter(e -> id.equals(e.entityId())))
        .isEmpty();
    assertThat(events.stream(InvoiceSyncEvent.class).filter(e -> id.equals(e.getInvoiceId())))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo("SENT");
              assertThat(e.getPaymentUrl()).isNull();
              assertThat(e.getPaymentSessionId()).isNull();
              assertThat(e.getTotal()).isEqualByComparingTo("321.00");
              assertThat(e.getTaxAmount()).isEqualByComparingTo("0");
            });
    assertThat(GreenMailTestSupport.getInstance().getReceivedMessages().length).isEqualTo(mail);
    // and the main batch's drain sent nothing either
    assertThat(mailAfter).isEqualTo(mailBefore);
    // (BF-PAY is left out: its later payments generate receipts, as for any fee note)
    for (UUID feeNote : List.of(id, feeNotes.get("BF-CUR"), feeNotes.get("BF-OLD"))) {
      assertThat(
              count(
                  "SELECT count(*) FROM %s.generated_documents WHERE primary_entity_id = ?",
                  feeNote))
          .isZero();
    }
  }

  // 6
  @Test
  void noInAppNotification_isCreatedForTheFeeNotes() {
    for (String number : List.of("BF-CUR", "BF-OLD", "BF-MAT")) {
      assertThat(
              count(
                  "SELECT count(*) FROM %s.notifications WHERE reference_entity_id = ?",
                  feeNotes.get(number)))
          .as(number)
          .isZero();
    }
  }

  // 7
  @Test
  void noAccountingSyncEntry_isEnqueued_whileAnOrdinaryFeeNoteInTheSameTenantIs() throws Exception {
    for (String number : List.of("BF-CUR", "BF-OLD", "BF-MAT")) {
      assertThat(syncEntriesFor(feeNotes.get(number))).as(number).isEmpty();
    }
    // Xero is CONNECTED here. The guard sits in enqueueInvoicePush itself (FD-28), so every
    // trigger is refused for a brought-forward fee note — called directly, as each listener does
    for (SyncTrigger trigger : SyncTrigger.values()) {
      for (String number : List.of("BF-CUR", "BF-OLD", "BF-MAT")) {
        enqueue(feeNotes.get(number), trigger);
        assertThat(syncEntriesFor(feeNotes.get(number))).as("%s %s", number, trigger).isEmpty();
      }
    }
    // ... while an ordinary fee note in the same tenant does get a PENDING entry (not vacuous)
    UUID ordinary = approvedOrdinaryFeeNote(thandi).id();
    enqueue(ordinary, SyncTrigger.EVENT);
    assertThat(syncEntriesFor(ordinary))
        .singleElement()
        .satisfies(e -> assertThat(e.getState()).isEqualTo(SyncState.PENDING));
  }

  // 8
  @Test
  void noAutomation_runsForTheFeeNotes() {
    for (String number : List.of("BF-CUR", "BF-OLD", "BF-MAT")) {
      assertThat(automationExecutions(feeNotes.get(number))).as(number).isZero();
    }
  }

  // 10
  @Test
  void thePortalFeeNote_isUpserted_withoutAPaymentUrl() {
    UUID id = feeNotes.get("BF-CUR");
    var portal = portalReadModel.findInvoiceById(id, ORG_ID).orElseThrow();
    assertThat(portal.status()).isEqualTo("SENT");
    assertThat(portal.invoiceNumber()).isEqualTo("BF-CUR");
    assertThat(portal.paymentUrl()).isNull();
    assertThat(portal.total()).isEqualByComparingTo(CUR_AMOUNT);
    assertThat(portalReadModel.findInvoiceLinesByInvoice(id))
        .singleElement()
        .satisfies(
            l ->
                assertThat(l.description())
                    .startsWith("Balance brought forward as at ")
                    .endsWith(" — fee note BF-CUR"));
  }

  // 11
  @Test
  void theCollectionsScan_noOpsWhileCollectionsAreSwitchedOff() {
    var result = inTenant(collectionsScan::scanForTenant);
    assertThat(result).isEqualTo(new CollectionsScanService.ScanResult(0, 0, 0, 0));
    for (UUID feeNote : feeNotes.values()) {
      assertThat(
              count("SELECT count(*) FROM %s.collection_activities WHERE invoice_id = ?", feeNote))
          .isZero();
    }
  }

  // 12
  @Test
  void theCashDigest_countsThemInOutstandingAndAging_butNotInBilledThisWeek() {
    BigDecimal sum = CUR_AMOUNT.add(OLD_AMOUNT).add(MAT_AMOUNT).add(VOID_AMOUNT).add(PAY_AMOUNT);
    assertThat(digestAfter.outstandingTotal().subtract(digestBefore.outstandingTotal()))
        .isEqualByComparingTo(sum);
    // BF-OLD is 100 days overdue: the 90+ bucket
    assertThat(digestAfter.buckets().d90plus().subtract(digestBefore.buckets().d90plus()))
        .isEqualByComparingTo(OLD_AMOUNT);
    // BF-MAT is 10 days overdue: the 1–30 bucket; the rest are not yet due: current
    assertThat(digestAfter.buckets().d30().subtract(digestBefore.buckets().d30()))
        .isEqualByComparingTo(MAT_AMOUNT);
    assertThat(digestAfter.buckets().current().subtract(digestBefore.buckets().current()))
        .isEqualByComparingTo(CUR_AMOUNT.add(VOID_AMOUNT).add(PAY_AMOUNT));
    // historic issue dates: nothing "billed this week"
    assertThat(digestAfter.billed()).isEqualByComparingTo(digestBefore.billed());
  }

  // 13
  @Test
  void invoiceGeneration_isStillEnforcedOnCreateDraft_forTheSameAddresslessClient()
      throws Exception {
    assertThat(invoice("BF-OLD").getCustomerId()).isEqualTo(bare);
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\": \"%s\", \"currency\": \"ZAR\"}".formatted(bare)))
        .andExpect(status().isUnprocessableEntity());
  }

  // 14
  @Test
  void aVoid_isAuditedAndFiresTheOrdinaryListeners_butIsNeverEnqueuedForXero() throws Exception {
    UUID id = feeNotes.get("BF-VOID");
    assertThat(portalReadModel.findInvoiceById(id, ORG_ID)).isPresent();
    String json =
        json(
            mockMvc
                .perform(post("/api/invoices/" + id + "/void").with(ownerJwt()))
                .andExpect(status().isOk()));
    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("VOID");
    assertThat(TakeOnTestApi.auditEvents(jdbc, schema, id))
        .extracting(TakeOnTestApi.AuditRow::eventType)
        .contains("invoice.voided");
    assertThat(events.stream(InvoiceVoidedEvent.class).filter(e -> id.equals(e.entityId())))
        .hasSize(1);
    assertThat(count("SELECT count(*) FROM %s.notifications WHERE reference_entity_id = ?", id))
        .isPositive();
    assertThat(automationExecutions(id)).isPositive();
    assertThat(portalReadModel.findInvoiceById(id, ORG_ID)).isEmpty();
    assertThat(syncEntriesFor(id)).isEmpty();
    enqueue(id, SyncTrigger.EVENT); // what onInvoiceVoided calls
    assertThat(syncEntriesFor(id)).isEmpty();
  }

  // 15
  @Test
  void aPayment_movesItToPartiallyPaidThenPaid_andIsNeverEnqueuedForXero() throws Exception {
    UUID id = feeNotes.get("BF-PAY");
    String part = pay(id, "400.00");
    assertThat((String) JsonPath.read(part, "$.status")).isEqualTo("PARTIALLY_PAID");
    String rest = pay(id, "600.00");
    assertThat((String) JsonPath.read(rest, "$.status")).isEqualTo("PAID");
    assertThat(invoice("BF-PAY").getAmountPaid()).isEqualByComparingTo(PAY_AMOUNT);
    assertThat(syncEntriesFor(id)).isEmpty();
    enqueue(id, SyncTrigger.FORCE_RESYNC);
    assertThat(syncEntriesFor(id)).isEmpty();
  }

  // 16
  @Test
  void theFeeNoteResponse_carriesTheTakeOnBatch_andAnOrdinaryOneCarriesNull() throws Exception {
    UUID id = feeNotes.get("BF-CUR");
    String detail =
        json(
            mockMvc
                .perform(get("/api/invoices/" + id).with(ownerJwt()))
                .andExpect(status().isOk()));
    assertThat((String) JsonPath.read(detail, "$.takeOnBatchId")).isEqualTo(batchId.toString());
    UUID ordinary = approvedOrdinaryFeeNote(thandi).id();
    String ordinaryDetail =
        json(
            mockMvc
                .perform(get("/api/invoices/" + ordinary).with(ownerJwt()))
                .andExpect(status().isOk()));
    assertThat((Object) JsonPath.read(ordinaryDetail, "$.takeOnBatchId")).isNull();
    String list =
        json(
            mockMvc
                .perform(get("/api/invoices").param("clientId", thandi.toString()).with(ownerJwt()))
                .andExpect(status().isOk()));
    List<String> listed = JsonPath.read(list, "$[?(@.id == '" + id + "')].takeOnBatchId");
    assertThat(listed).containsExactly(batchId.toString());
    List<Object> listedOrdinary =
        JsonPath.read(list, "$[?(@.id == '" + ordinary + "')].takeOnBatchId");
    assertThat(listedOrdinary).hasSize(1).containsOnlyNulls();
  }

  // 17
  @Test
  void theAudit_isInvoiceCreatedAndBroughtForwardIssued_withIdsOnly() {
    for (String number : List.of("BF-CUR", "BF-OLD", "BF-MAT")) {
      UUID id = feeNotes.get(number);
      var audit = TakeOnTestApi.auditEvents(jdbc, schema, id);
      assertThat(audit)
          .extracting(TakeOnTestApi.AuditRow::eventType)
          .as(number)
          .containsExactly("invoice.created", "invoice.brought_forward_issued");
      assertThat(audit.getFirst().details())
          .isEqualTo(Map.of("source", "TAKE_ON", "take_on_batch_id", batchId.toString()));
      assertThat(audit.get(1).details())
          .isEqualTo(Map.of("invoice_id", id.toString(), "take_on_batch_id", batchId.toString()));
    }
  }

  // --- helpers ---

  /** Uploads, dates, maps, validates and commits one debtors batch; returns the batch id. */
  private UUID commitDebtors(TakeOnDebtorsFiles.Row... rows) throws Exception {
    var batch =
        TakeOnTestApi.validatedDebtors(
            mockMvc,
            ownerJwt(),
            TakeOnDebtorsFiles.csv(rows),
            TakeOnDebtorsFiles.mapping(),
            CUTOVER.toString());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.error")).isZero();
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, batch.batchId(), batch.hash());
    String detail = json(TakeOnTestApi.detail(mockMvc, ownerJwt(), batch.batchId()));
    assertThat((String) JsonPath.read(detail, "$.status")).isEqualTo("COMPLETED");
    assertThat((Integer) JsonPath.read(detail, "$.counts.created")).isEqualTo(rows.length);
    return batch.batchId();
  }

  private record Ordinary(UUID id, String number) {}

  /** An ordinary Kazi fee note: createDraft + a manual line + approve. */
  private Ordinary approvedOrdinaryFeeNote(UUID clientId) throws Exception {
    String draft =
        json(
            mockMvc
                .perform(
                    post("/api/invoices")
                        .with(ownerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"clientId\": \"%s\", \"currency\": \"ZAR\"}".formatted(clientId)))
                .andExpect(status().isCreated()));
    String id = JsonPath.read(draft, "$.id");
    mockMvc
        .perform(
            post("/api/invoices/" + id + "/lines")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"description\": \"Consultation\", \"quantity\": 1, \"unitPrice\": 250.00,"
                        + " \"sortOrder\": 0}"))
        .andExpect(status().isCreated());
    String approved =
        json(
            mockMvc
                .perform(post("/api/invoices/" + id + "/approve").with(ownerJwt()))
                .andExpect(status().isOk()));
    return new Ordinary(UUID.fromString(id), JsonPath.read(approved, "$.invoiceNumber"));
  }

  private String pay(UUID id, String amount) throws Exception {
    return json(
        mockMvc
            .perform(
                post("/api/invoices/" + id + "/payment")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"amount\": %s, \"paymentMethod\": \"EFT\", \"paymentReference\": \"EFT-1\"}"
                            .formatted(amount)))
            .andExpect(status().isOk()));
  }

  private Customer kaziReady(String name, String email) {
    return TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(name, email, ownerId);
  }

  private void createConnectedXeroConnection() {
    var orgIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
    orgIntegration.enable();
    var savedIntegration = orgIntegrations.save(orgIntegration);
    xeroConnections.save(
        new AccountingXeroConnection(
            savedIntegration.getId(),
            "xero-tenant-" + UUID.randomUUID().toString().substring(0, 8),
            "BF Xero Org",
            UUID.randomUUID(),
            Instant.now().plus(30, ChronoUnit.MINUTES),
            "accounting.transactions openid profile email"));
  }

  /** A matter created in the UI ({@code POST /api/matters}), with its client and reference. */
  private UUID createMatter(String name, UUID clientId, String referenceNumber) throws Exception {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    body.put("clientId", clientId.toString());
    body.put("referenceNumber", referenceNumber);
    String created =
        json(
            mockMvc
                .perform(
                    post("/api/matters")
                        .with(ownerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonMapper.builder().build().writeValueAsString(body)))
                .andExpect(status().isCreated()));
    return UUID.fromString(JsonPath.read(created, "$.id"));
  }

  private Invoice invoice(String number) {
    return inTenant(() -> invoices.findByInvoiceNumber(number).orElseThrow());
  }

  private List<io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncEntry>
      syncEntriesFor(UUID invoiceId) {
    return inTenant(() -> syncEntries.findByEntity(SyncEntityType.INVOICE, invoiceId));
  }

  private void enqueue(UUID invoiceId, SyncTrigger trigger) {
    inTenant(
        () -> {
          accountingSync.enqueueInvoicePush(invoiceId, trigger);
          return null;
        });
  }

  private long automationExecutions(UUID invoiceId) {
    return count(
        "SELECT count(*) FROM %s.automation_executions WHERE trigger_event_data ->> 'entityId' = ?",
        invoiceId.toString());
  }

  /** The firm's fee-note counter ({@code invoice_counters.next_number}); empty when never used. */
  private List<Long> counter() {
    return jdbc.queryForList(
        "SELECT next_number FROM \"%s\".invoice_counters".formatted(schema), Long.class);
  }

  private long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql.formatted("\"" + schema + "\""), Long.class, args);
    return n == null ? 0 : n;
  }

  private static String json(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private <T> T inTenant(Callable<T> action) {
    return RequestScopes.callForTenant(schema, ORG_ID, action);
  }

  private JwtRequestPostProcessor ownerJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
