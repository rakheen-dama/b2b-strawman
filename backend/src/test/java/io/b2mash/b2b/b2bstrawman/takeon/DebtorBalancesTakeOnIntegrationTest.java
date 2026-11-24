package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnDebtorsFiles.csv;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnDebtorsFiles.mapping;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnDebtorsFiles.row;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.detail;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rowContent;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rows;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validatedDebtors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.Validated;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.DebtorBalancesDatasetHandler;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 93 658A (task 658.6, §17.3.10): the debtor opening-balances take-on through the endpoints
 * the wizard uses — upload → cutover date → map → validate → commit ({@link TakeOnTestDrain}; no
 * sign-off) → results. A row resolves its client by email, previous-system ID or ID number; the
 * matter, when given, must be the client's; the amount is more than nil; the issue date is on or
 * before the cutover date; the currency is the firm's; a blank due date defaults to issue date plus
 * the firm's terms (warning); the source number is kept — a number already in Kazi is an error,
 * except an earlier brought-forward fee note of the same client and amount (duplicate); a client
 * without an address or email is a warning only (FD-21). Every assertion is id-scoped to this
 * test's own rows. The quiet path's side effects are pinned in {@code
 * BroughtForwardFeeNoteIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DebtorBalancesTakeOnIntegrationTest {

  private static final String ORG_ID = "org_debtors_takeon_test";
  private static final String OWNER = "user_dbt_owner";
  private static final String OWNER_EMAIL = "dbt_owner@test.com";
  private static final String CUTOVER = "2026-10-31";
  private static final String THANDI_EMAIL = "db.thandi@example.test";
  private static final String SIPHO_EMAIL = "db.sipho@example.test";
  private static final String BARE_EMAIL = "db.bare@example.test";
  private static final String TAKEN_ON_REF = "DB-C-100";
  private static final String ID_NUMBER = "7702025009083";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private CustomerRepository customers;
  @Autowired private InvoiceRepository invoices;

  private String schema;
  private UUID ownerId;
  private UUID thandi; // ACTIVE, Kazi-ready (address + email): passes INVOICE_GENERATION
  private UUID sipho; // ACTIVE, Kazi-ready, holds an ID number
  private UUID bare; // ACTIVE, email only — no billing address
  private UUID takenOn; // a client taken on by an earlier clients batch (previous-system ID)

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Debtors Take-on Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, OWNER_EMAIL, "Debtors Owner", "owner"));
    thandi = kaziReadyClient("Thandi Nkosi", THANDI_EMAIL, null);
    sipho = kaziReadyClient("Sipho Dube", SIPHO_EMAIL, ID_NUMBER);
    bare =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Bare Client", BARE_EMAIL, ownerId))
                    .getId());

    var clients =
        TakeOnTestApi.validatedClients(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, OWNER),
            TakeOnClientsFiles.csv(
                TakeOnClientsFiles.row("Lerato Molefe", "db.lerato@example.test")
                    .ref(TAKEN_ON_REF)),
            TakeOnClientsFiles.mapping());
    TakeOnTestApi.attest(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, OWNER),
            clients.batchId(),
            clients.hash(),
            Map.of())
        .andExpect(status().isOk());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, clients.batchId(), clients.hash());
    takenOn =
        jdbc.queryForObject(
            "SELECT result_entity_id FROM \"%s\".take_on_rows WHERE batch_id = ? AND source_ref = ?"
                .formatted(schema),
            UUID.class,
            clients.batchId(),
            TAKEN_ON_REF);
  }

  // 1
  @Test
  void theRequiredFields_andANilOrNegativeAmount_areErrors() throws Exception {
    var batch =
        validate(
            row("", "DB-REQ-1").ref("R1-1"),
            row(THANDI_EMAIL, "").ref("R1-2"),
            row(THANDI_EMAIL, "DB-REQ-3").issue("").ref("R1-3"),
            row(THANDI_EMAIL, "DB-REQ-4").amount("").ref("R1-4"),
            row(THANDI_EMAIL, "DB-REQ-5").amount("0").ref("R1-5"),
            row(THANDI_EMAIL, "DB-REQ-6").amount("-5").ref("R1-6"),
            row(THANDI_EMAIL, "DB-REQ-7").ref("R1-7"));
    var rows = rowsByRef(batch.batchId());
    assertError(rows.get("R1-1"), "REQUIRED", "clientRef", "Client reference is required.");
    assertError(rows.get("R1-2"), "REQUIRED", "feeNoteNumber", "Fee note number is required.");
    assertError(rows.get("R1-3"), "REQUIRED", "issueDate", "Issue date is required.");
    assertError(
        rows.get("R1-4"), "REQUIRED", "outstandingAmount", "Outstanding amount is required.");
    for (String ref : List.of("R1-5", "R1-6")) {
      assertError(
          rows.get(ref),
          TrustPositionMessages.AMOUNT_NOT_POSITIVE,
          "outstandingAmount",
          TrustPositionMessages.AMOUNT_NOT_POSITIVE_MESSAGE);
    }
    assertThat(rows.get("R1-7").get("status")).isEqualTo("VALIDATED");
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(1);
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.error")).isEqualTo(6);
  }

  // 2
  @Test
  void theClient_resolvesByEmailPreviousSystemIdOrIdNumber_andAnOrphanOrAmbiguousOneIsAnError()
      throws Exception {
    activeClientWithIdNumber("Twin One", "db.twin1@example.test", "6602025009084");
    activeClientWithIdNumber("Twin Two", "db.twin2@example.test", "6602025009084");
    var batch =
        validate(
            row("DB.Thandi@example.test", "DB-CL-1").ref("R2-1"),
            row("db-c-100", "DB-CL-2").ref("R2-2"),
            row(ID_NUMBER, "DB-CL-3").ref("R2-3"),
            row("nobody@example.test", "DB-CL-4").ref("R2-4"),
            row("6602025009084", "DB-CL-5").ref("R2-5"));
    var rows = rowsByRef(batch.batchId());
    assertResolved(rows.get("R2-1"), thandi, "email");
    assertResolved(rows.get("R2-2"), takenOn, "previousSystemId");
    assertResolved(rows.get("R2-3"), sipho, "idNumber");
    assertError(
        rows.get("R2-4"),
        "CLIENT_UNRESOLVED",
        "clientRef",
        "No client matches nobody@example.test. Take the client on first, or correct the"
            + " reference.");
    assertError(
        rows.get("R2-5"),
        "CLIENT_AMBIGUOUS",
        "clientRef",
        "More than one client has the ID number 6602025009084. Use the client's email or"
            + " previous-system ID instead.");
    // no sign-off for debtors: COMMIT is offered straight after a clean dry run, never ATTEST
    assertThat((List<String>) JsonPath.read(batch.json(), "$.allowedActions"))
        .doesNotContain("ATTEST");
  }

  // 3
  @Test
  void theMatter_mustExistAndBeTheClients() throws Exception {
    UUID thandisMatter = createMatter("Thandi's estate", thandi, "DB-M-1");
    createMatter("Sipho's transfer", sipho, "DB-M-2");
    var batch =
        validate(
            row(THANDI_EMAIL, "DB-MT-1").matter("db-m-1").ref("R3-1"),
            row(THANDI_EMAIL, "DB-MT-2").matter("DB-M-2").ref("R3-2"),
            row(THANDI_EMAIL, "DB-MT-3").matter("DB-M-404").ref("R3-3"));
    var rows = rowsByRef(batch.batchId());
    assertThat(rows.get("R3-1").get("status")).isEqualTo("VALIDATED");
    assertThat(values(rows.get("R3-1"))).containsEntry("matterId", thandisMatter.toString());
    assertError(
        rows.get("R3-2"),
        TrustPositionMessages.CLIENT_MATTER_MISMATCH,
        "matterRef",
        "Matter DB-M-2 is not a matter of client " + THANDI_EMAIL + ".");
    assertError(
        rows.get("R3-3"),
        TrustPositionMessages.MATTER_UNRESOLVED,
        "matterRef",
        TrustPositionMessages.matterUnresolved("DB-M-404"));

    commit(batch);
    UUID created = resultEntity(rowsByRef(batch.batchId()).get("R3-1"));
    var line =
        jdbc.queryForMap(
            "SELECT project_id FROM \"%s\".invoice_lines WHERE invoice_id = ?".formatted(schema),
            created);
    assertThat(line.get("project_id")).isEqualTo(thandisMatter);
  }

  // 4
  @Test
  void aNumberAlreadyInKazi_isAnError_exceptAnEarlierBroughtForwardNoteOfTheSameClientAndAmount()
      throws Exception {
    String kaziNumber = approvedKaziFeeNote(thandi);
    String kaziNumberLower = kaziNumber.toLowerCase(Locale.ROOT);
    var earlier =
        validate(
            row(THANDI_EMAIL, "DB-DUP-1").amount("500.00").ref("R4a-1"),
            row(THANDI_EMAIL, "DB-DUP-2").amount("700.00").ref("R4a-2"),
            row(THANDI_EMAIL, "DB-DUP-3").amount("300.00").ref("R4a-3"));
    commit(earlier);
    UUID dup1 = resultEntity(rowsByRef(earlier.batchId()).get("R4a-1"));

    var batch =
        validate(
            row(THANDI_EMAIL, "db-dup-1").amount("500").ref("R4-1"),
            row(SIPHO_EMAIL, "DB-DUP-2").amount("700.00").ref("R4-2"),
            row(THANDI_EMAIL, "DB-DUP-3").amount("999.00").ref("R4-3"),
            row(THANDI_EMAIL, kaziNumberLower).ref("R4-4"),
            row(THANDI_EMAIL, "DB-NEW-5").ref("R4-5"),
            row(THANDI_EMAIL, "db-new-5").ref("R4-6"));
    var rows = rowsByRef(batch.batchId());
    assertThat(rows.get("R4-1").get("status")).isEqualTo("VALIDATED");
    assertThat(rows.get("R4-1").get("duplicateOfId")).isEqualTo(dup1.toString());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.duplicate")).isEqualTo(1);
    assertError(
        rows.get("R4-2"),
        DebtorBalancesDatasetHandler.FEE_NOTE_NUMBER_EXISTS,
        "feeNoteNumber",
        "Fee-note number DB-DUP-2 already exists in Kazi");
    assertError(
        rows.get("R4-3"),
        DebtorBalancesDatasetHandler.FEE_NOTE_NUMBER_EXISTS,
        "feeNoteNumber",
        "Fee-note number DB-DUP-3 already exists in Kazi");
    assertError(
        rows.get("R4-4"),
        DebtorBalancesDatasetHandler.FEE_NOTE_NUMBER_EXISTS,
        "feeNoteNumber",
        "Fee-note number " + kaziNumberLower + " already exists in Kazi");
    assertError(
        rows.get("R4-6"),
        "DUPLICATE_IN_FILE",
        "feeNoteNumber",
        "Duplicate of row %d in this file".formatted((Integer) rows.get("R4-5").get("rowNumber")));

    commit(batch);
    var after = rowsByRef(batch.batchId());
    assertThat(after.get("R4-1").get("status")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(resultEntity(after.get("R4-1"))).isEqualTo(dup1);
    assertThat(resultEntityType(batch.batchId(), "R4-1")).isEqualTo("invoice");
    assertThat(after.get("R4-5").get("status")).isEqualTo("CREATED");
    assertThat(
            count("SELECT count(*) FROM %s.invoices WHERE lower(invoice_number) = ?", "db-dup-1"))
        .isEqualTo(1);
    assertThat(
            count("SELECT count(*) FROM %s.invoices WHERE lower(invoice_number) = ?", "db-new-5"))
        .isEqualTo(1);
  }

  // 5
  @Test
  void aBlankDueDate_defaultsToTheIssueDatePlusTheFirmsTerms_withAWarning() throws Exception {
    var batch =
        validate(
            row(THANDI_EMAIL, "DB-DUE-1").issue("15/09/2026").ref("R5-1"),
            row(THANDI_EMAIL, "DB-DUE-2").issue("15/09/2026").due("01/10/2026").ref("R5-2"));
    var rows = rowsByRef(batch.batchId());
    LocalDate defaulted = LocalDate.of(2026, 10, 15); // + 30 days, the default terms
    assertThat(rows.get("R5-1").get("status")).isEqualTo("VALIDATED");
    assertThat(values(rows.get("R5-1"))).containsEntry("dueDate", defaulted.toString());
    assertWarning(
        rows.get("R5-1"),
        DebtorBalancesDatasetHandler.DUE_DATE_DEFAULTED,
        "dueDate",
        "No due date was given. It is set to 15 October 2026, the issue date plus the firm's"
            + " payment terms of 30 days.");
    assertThat(warnings(rows.get("R5-2")))
        .noneMatch(w -> DebtorBalancesDatasetHandler.DUE_DATE_DEFAULTED.equals(w.get("code")));

    commit(batch);
    var after = rowsByRef(batch.batchId());
    var first = inTenant(() -> invoices.findById(resultEntity(after.get("R5-1"))).orElseThrow());
    assertThat(first.getDueDate()).isEqualTo(defaulted);
    assertThat(first.getIssueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    var second = inTenant(() -> invoices.findById(resultEntity(after.get("R5-2"))).orElseThrow());
    assertThat(second.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
  }

  // 6
  @Test
  void aForeignCurrency_isAnError_andABlankOrMatchingOneIsTheFirms() throws Exception {
    var batch =
        validate(
            row(THANDI_EMAIL, "DB-CUR-1").currency("USD").ref("R6-1"),
            row(THANDI_EMAIL, "DB-CUR-2").ref("R6-2"),
            row(THANDI_EMAIL, "DB-CUR-3").currency("zar").ref("R6-3"));
    var rows = rowsByRef(batch.batchId());
    assertError(
        rows.get("R6-1"),
        DebtorBalancesDatasetHandler.CURRENCY_MISMATCH,
        "currency",
        "Take on fee notes in ZAR only. This fee note is in USD.");
    assertThat(values(rows.get("R6-2"))).containsEntry("currency", "ZAR");
    assertThat(values(rows.get("R6-3"))).containsEntry("currency", "ZAR");
    commit(batch);
    var after = rowsByRef(batch.batchId());
    for (String ref : List.of("R6-2", "R6-3")) {
      var invoice = inTenant(() -> invoices.findById(resultEntity(after.get(ref))).orElseThrow());
      assertThat(invoice.getCurrency()).isEqualTo("ZAR");
    }
  }

  // 7
  @Test
  void aClientWithoutABillingAddress_isOnlyAWarning_andItsFeeNoteIsStillTakenOn() throws Exception {
    var batch =
        validate(
            row(BARE_EMAIL, "DB-ADR-1").ref("R7-1"), row(THANDI_EMAIL, "DB-ADR-2").ref("R7-2"));
    var rows = rowsByRef(batch.batchId());
    assertThat(rows.get("R7-1").get("status")).isEqualTo("VALIDATED");
    assertWarning(
        rows.get("R7-1"),
        DebtorBalancesDatasetHandler.CLIENT_DETAILS_MISSING,
        "clientRef",
        DebtorBalancesDatasetHandler.CLIENT_DETAILS_MISSING_MESSAGE);
    assertThat(warnings(rows.get("R7-2")))
        .noneMatch(w -> DebtorBalancesDatasetHandler.CLIENT_DETAILS_MISSING.equals(w.get("code")));

    commit(batch);
    var after = rowsByRef(batch.batchId());
    assertThat(after.get("R7-1").get("status")).isEqualTo("CREATED");
    var invoice = inTenant(() -> invoices.findById(resultEntity(after.get("R7-1"))).orElseThrow());
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    assertThat(invoice.getCustomerId()).isEqualTo(bare);
  }

  // 8
  @Test
  void anIssueDateAfterTheCutoverDate_isAnError() throws Exception {
    var batch =
        validate(
            row(THANDI_EMAIL, "DB-CUT-1").issue("15/11/2026").ref("R8-1"),
            row(THANDI_EMAIL, "DB-CUT-2").issue("31/10/2026").ref("R8-2"));
    var rows = rowsByRef(batch.batchId());
    assertError(
        rows.get("R8-1"),
        TrustPositionMessages.DATE_AFTER_CUTOVER,
        "issueDate",
        TrustPositionMessages.dateAfterCutover(
            LocalDate.of(2026, 11, 15), LocalDate.parse(CUTOVER)));
    assertThat(rows.get("R8-2").get("status")).isEqualTo("VALIDATED");
  }

  // 9
  @Test
  void aCommitThatStopsMidBatch_resumesWhereItStopped_andIssuesEachFeeNoteOnce() throws Exception {
    var batch =
        validate(
            row(THANDI_EMAIL, "DB-RES-1").ref("R9-1"),
            row(THANDI_EMAIL, "DB-RES-2").ref("R9-2"),
            row(THANDI_EMAIL, "DB-RES-3").ref("R9-3"));
    TakeOnTestDrain.commit(commitService, schema, ORG_ID, ownerId, batch.batchId(), batch.hash());
    TakeOnTestDrain.run(drain, schema, ORG_ID, Duration.ZERO); // stops after one row
    var mid = json(detail(mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), batch.batchId()));
    assertThat((String) JsonPath.read(mid, "$.status")).isEqualTo("COMMITTING");
    assertThat((Integer) JsonPath.read(mid, "$.counts.created")).isEqualTo(1);

    TakeOnTestDrain.run(drain, schema, ORG_ID);
    TakeOnTestDrain.run(drain, schema, ORG_ID); // a redelivered run finds nothing to do
    var done = json(detail(mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), batch.batchId()));
    assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("COMPLETED");
    assertThat((Integer) JsonPath.read(done, "$.counts.created")).isEqualTo(3);
    for (String number : List.of("DB-RES-1", "DB-RES-2", "DB-RES-3")) {
      assertThat(count("SELECT count(*) FROM %s.invoices WHERE invoice_number = ?", number))
          .isEqualTo(1);
    }
    var audit = TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId());
    assertThat(audit).filteredOn(a -> a.eventType().equals("takeon.batch.committed")).hasSize(1);
    // the commit needed no sign-off: commit_requested carries no sign_off_id (D-90)
    assertThat(audit)
        .filteredOn(a -> a.eventType().equals("takeon.batch.commit_requested"))
        .singleElement()
        .satisfies(a -> assertThat(a.details()).containsOnlyKeys("batch_id", "dataset"));
  }

  // 10
  @Test
  void anOffboardedClient_isAnError_withTheGuardsText() throws Exception {
    UUID offboarded =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createCustomerWithStatus(
                            "Gone Client",
                            "db.offboarded@example.test",
                            ownerId,
                            LifecycleStatus.OFFBOARDED))
                    .getId());
    var batch =
        validate(
            row("db.offboarded@example.test", "DB-LC-1").ref("R10-1"),
            row(THANDI_EMAIL, "DB-LC-2").ref("R10-2"));
    var rows = rowsByRef(batch.batchId());
    assertError(
        rows.get("R10-1"),
        DebtorBalancesDatasetHandler.CLIENT_LIFECYCLE,
        "clientRef",
        "Cannot create fee note for client in OFFBOARDED lifecycle status");
    assertThat(rows.get("R10-2").get("status")).isEqualTo("VALIDATED");
    commit(batch);
    assertThat(inTenant(() -> invoices.findByCustomerId(offboarded))).isEmpty();
  }

  // 11 (S-14)
  @Test
  void aClientAnonymisedBetweenValidationAndCommit_redactsItsRows_andTheyFailWithTheClientGoneText()
      throws Exception {
    UUID gone =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Gone Debtor", "db.gone@example.test", ownerId))
                    .getId());
    var batch =
        validate(
            row("db.gone@example.test", "DB-ANON-1").ref("R11-1"),
            row(THANDI_EMAIL, "DB-ANON-2").ref("R11-2"));
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(2);

    mockMvc
        .perform(
            post("/api/clients/" + gone + "/anonymize")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationName\": \"Gone Debtor\", \"reason\": \"test\"}"))
        .andExpect(status().isOk());
    var redacted = rowByNumber(batch.batchId(), 2);
    assertThat(redacted.get("status")).isEqualTo("VALIDATED");
    assertThat((String) redacted.get("raw")).isEqualTo("{\"redacted\": true}");
    assertThat((String) redacted.get("mapped")).isEqualTo("{\"redacted\": true}");
    assertThat(redacted.get("source_ref")).isNull();
    assertThat(redacted.get("dedup_key")).isNull();

    commit(batch);
    var failed = rowByNumber(batch.batchId(), 2);
    assertThat(failed.get("status")).isEqualTo("FAILED");
    assertThat(failed.get("failure_reason"))
        .isEqualTo(DebtorBalancesDatasetHandler.CLIENT_GONE_MESSAGE);
    var other = rowByNumber(batch.batchId(), 3);
    assertThat(other.get("status")).isEqualTo("CREATED");
    assertThat((String) other.get("mapped")).contains(thandi.toString());
    assertThat(inTenant(() -> invoices.findByCustomerId(gone))).isEmpty();
  }

  // 12 (D-254): a VOID brought-forward fee note keeps its number — never a duplicate
  @Test
  void aVoidedBroughtForwardFeeNote_isNotADuplicate_itsNumberIsAnError() throws Exception {
    var first = validate(row(THANDI_EMAIL, "DB-VOID-1").amount("450.00").ref("R12-1"));
    commit(first);
    UUID voided = resultEntity(rowsByRef(first.batchId()).get("R12-1"));
    mockMvc
        .perform(
            post("/api/invoices/" + voided + "/void").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());

    var again = validate(row(THANDI_EMAIL, "DB-VOID-1").amount("450.00").ref("R12-2"));
    var rows = rowsByRef(again.batchId());
    assertError(
        rows.get("R12-2"),
        DebtorBalancesDatasetHandler.FEE_NOTE_NUMBER_EXISTS,
        "feeNoteNumber",
        "Fee-note number DB-VOID-1 already exists in Kazi");
    assertThat(rows.get("R12-2").get("duplicateOfId")).isNull();
    assertThat((Integer) JsonPath.read(again.json(), "$.counts.duplicate")).isZero();
  }

  // 13 (D-256): the matter moved to another client between the dry run and the commit
  @Test
  void aMatterMovedToAnotherClientAfterTheDryRun_failsItsRow_andNoFeeNoteIsIssued()
      throws Exception {
    UUID moving = createMatter("Matter that moves", thandi, "DB-MOVE-1");
    var batch =
        validate(
            row(THANDI_EMAIL, "DB-MOVE-FN").matter("DB-MOVE-1").ref("R13-1"),
            row(THANDI_EMAIL, "DB-MOVE-OK").ref("R13-2"));
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(2);

    // move the matter to Sipho and unlink it from Thandi (PUT /api/matters, then the client link)
    var body = new HashMap<String, Object>();
    body.put("name", "Matter that moves");
    body.put("clientId", sipho.toString());
    mockMvc
        .perform(
            put("/api/matters/" + moving)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(body)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/api/clients/" + thandi + "/matters/" + moving)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().is2xxSuccessful());

    commit(batch);
    var failed = rowByNumber(batch.batchId(), 2);
    assertThat(failed.get("status")).isEqualTo("FAILED");
    assertThat(failed.get("failure_reason"))
        .isEqualTo(DebtorBalancesDatasetHandler.MATTER_MOVED_MESSAGE);
    assertThat(count("SELECT count(*) FROM %s.invoices WHERE invoice_number = ?", "DB-MOVE-FN"))
        .isZero();
    assertThat(rowByNumber(batch.batchId(), 3).get("status")).isEqualTo("CREATED");
  }

  // 14 (D-255): the firm's currency changed between the dry run and the commit
  @Test
  void aFirmCurrencyChangedAfterTheDryRun_failsTheRow_insteadOfIssuingInAnotherCurrency()
      throws Exception {
    var batch = validate(row(THANDI_EMAIL, "DB-CCY-1").ref("R14-1"));
    assertThat(values(rowsByRef(batch.batchId()).get("R14-1"))).containsEntry("currency", "ZAR");
    setFirmCurrency("USD");
    try {
      commit(batch);
    } finally {
      setFirmCurrency("ZAR");
    }
    var failed = rowByNumber(batch.batchId(), 2);
    assertThat(failed.get("status")).isEqualTo("FAILED");
    assertThat(failed.get("failure_reason"))
        .isEqualTo(DebtorBalancesDatasetHandler.CURRENCY_CHANGED_MESSAGE);
    assertThat(count("SELECT count(*) FROM %s.invoices WHERE invoice_number = ?", "DB-CCY-1"))
        .isZero();
  }

  // 15: custom fields validated at write time, then the parked columns kept as takeon_parked
  @Test
  void theParkedColumns_areKeptOnTheFeeNote() throws Exception {
    var batch = validate(row(THANDI_EMAIL, "DB-PARK-1").ledger("LEDGER-77").ref("R15-1"));
    commit(batch);
    UUID created = resultEntity(rowsByRef(batch.batchId()).get("R15-1"));
    String customFields =
        jdbc.queryForObject(
            "SELECT custom_fields::text FROM \"%s\".invoices WHERE id = ?".formatted(schema),
            String.class,
            created);
    assertThat(customFields).contains("\"takeon_parked\"").contains("LEDGER-77");
  }

  // --- helpers ---

  private void setFirmCurrency(String currency) throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"defaultCurrency\": \"%s\"}".formatted(currency)))
        .andExpect(status().isOk());
  }

  private Validated validate(TakeOnDebtorsFiles.Row... rows) throws Exception {
    return validatedDebtors(
        mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), csv(rows), mapping(), CUTOVER);
  }

  private void commit(Validated batch) {
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, batch.batchId(), batch.hash());
  }

  /** An ACTIVE client with the billing details INVOICE_GENERATION needs (address + email). */
  private UUID kaziReadyClient(String name, String email, String idNumber) {
    return inTenant(
        () -> {
          var c =
              new Customer(
                  name,
                  email,
                  null,
                  idNumber,
                  null,
                  ownerId,
                  CustomerType.INDIVIDUAL,
                  LifecycleStatus.ACTIVE);
          c.setCustomFields(new HashMap<>(TestCustomerFactory.prerequisiteCustomFields()));
          c.setAddressLine1("123 Test Street");
          c.setCity("Test City");
          c.setCountry("ZA");
          c.setTaxNumber("VAT123456");
          c.setContactName("Test Contact");
          c.setContactEmail("contact@test.com");
          return customers.save(c).getId();
        });
  }

  private UUID activeClientWithIdNumber(String name, String email, String idNumber) {
    return inTenant(
        () ->
            customers
                .save(
                    new Customer(
                        name,
                        email,
                        null,
                        idNumber,
                        null,
                        ownerId,
                        CustomerType.INDIVIDUAL,
                        LifecycleStatus.ACTIVE))
                .getId());
  }

  /** A matter created in the UI ({@code POST /api/matters}), with its client and reference. */
  private UUID createMatter(String name, UUID clientId, String referenceNumber) throws Exception {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    body.put("clientId", clientId.toString());
    body.put("referenceNumber", referenceNumber);
    var result =
        mockMvc
            .perform(
                post("/api/matters")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JsonMapper.builder().build().writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  /** An ordinary Kazi fee note: createDraft + a manual line + approve; returns its number. */
  private String approvedKaziFeeNote(UUID clientId) throws Exception {
    String draft =
        json(
            mockMvc.perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientId\": \"%s\", \"currency\": \"ZAR\"}".formatted(clientId))));
    String id = JsonPath.read(draft, "$.id");
    mockMvc
        .perform(
            post("/api/invoices/" + id + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"description\": \"Consultation\", \"quantity\": 1, \"unitPrice\": 250.00,"
                        + " \"sortOrder\": 0}"))
        .andExpect(status().isCreated());
    String approved =
        json(
            mockMvc
                .perform(
                    post("/api/invoices/" + id + "/approve")
                        .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
                .andExpect(status().isOk()));
    return JsonPath.read(approved, "$.invoiceNumber");
  }

  private Map<String, Object> rowByNumber(UUID batchId, int rowNumber) {
    return jdbc.queryForMap(
        ("SELECT status, raw_values::text AS raw, mapped_values::text AS mapped, dedup_key,"
                + " source_ref, failure_reason FROM \"%s\".take_on_rows WHERE batch_id = ? AND row_number = ?")
            .formatted(schema),
        batchId,
        rowNumber);
  }

  private Map<String, Map<String, Object>> rowsByRef(UUID batchId) throws Exception {
    return rowContent(
            json(rows(mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), batchId, "size=200")))
        .stream()
        .collect(Collectors.toMap(r -> (String) r.get("sourceRef"), r -> r));
  }

  private String resultEntityType(UUID batchId, String ref) {
    return jdbc.queryForObject(
        "SELECT result_entity_type FROM \"%s\".take_on_rows WHERE batch_id = ? AND source_ref = ?"
            .formatted(schema),
        String.class,
        batchId,
        ref);
  }

  private static UUID resultEntity(Map<String, Object> row) {
    return UUID.fromString((String) row.get("resultEntityId"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> values(Map<String, Object> row) {
    return (Map<String, Object>) row.get("values");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> warnings(Map<String, Object> row) {
    return (List<Map<String, Object>>) row.get("warnings");
  }

  private static void assertResolved(Map<String, Object> row, UUID clientId, String resolvedBy) {
    assertThat(row.get("status")).isEqualTo("VALIDATED");
    assertThat(values(row))
        .containsEntry("clientId", clientId.toString())
        .containsEntry("clientResolvedBy", resolvedBy);
  }

  @SuppressWarnings("unchecked")
  private static void assertError(
      Map<String, Object> row, String code, String field, String message) {
    assertThat(row.get("status")).isEqualTo("ERROR");
    assertThat((List<Map<String, Object>>) row.get("errors"))
        .anySatisfy(
            e -> {
              assertThat(e.get("code")).isEqualTo(code);
              assertThat(e.get("field")).isEqualTo(field);
              assertThat(e.get("message")).isEqualTo(message);
            });
  }

  private static void assertWarning(
      Map<String, Object> row, String code, String field, String message) {
    assertThat(warnings(row))
        .anySatisfy(
            w -> {
              assertThat(w.get("code")).isEqualTo(code);
              assertThat(w.get("field")).isEqualTo(field);
              assertThat(w.get("message")).isEqualTo(message);
            });
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
}
