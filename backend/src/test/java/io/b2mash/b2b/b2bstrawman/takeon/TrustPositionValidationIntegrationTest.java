package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.addSource;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.create;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.detail;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.map;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.position;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rowContent;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rows;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validate;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustFiles.CLIENT_LEDGER;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustFiles.INVESTMENTS;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustFiles.OUTSTANDING_ITEMS;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustFiles.UNIDENTIFIED_DEPOSITS;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustFiles.section17313;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustFiles.section17313Position;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustFiles.withFigure;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustFiles.withRows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Phase 93 657B (task 657.15, §17.3.9): the trust position dry run through the endpoints the wizard
 * uses — multipart upload of the four sections → mapping → {@code PUT …/position} → {@code POST
 * …/validate} — on the architecture §17.3.13 figures. Clients A, B, C are taken on by a clients
 * batch and matters A1, B1, C1 by a matters batch (committed through {@link TakeOnTestDrain}), so
 * the trust files name them as a firm would: the previous system's client id and the matter
 * reference. Nothing is posted by the dry run; the account-check preconditions are built through
 * the real posting paths (the trust services, the 657A harness {@code postOpening}), never SQL.
 * Every banking account that needs its own state is fresh; every assertion is scoped to this test's
 * own batch or account (id-scope).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustPositionValidationIntegrationTest {

  private static final String ORG_ID = "org_trust_position_validation_test";
  private static final String OWNER = "user_tpv_owner";
  private static final String ADMIN = "user_tpv_admin";
  private static final String C = "2026-10-31";
  private static final LocalDate CUTOVER = LocalDate.parse(C);
  private static final String NBSP = " ";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustAccountRepository trustAccounts;

  private final AtomicInteger accounts = new AtomicInteger();
  private String schema;
  private UUID ownerId;
  private UUID adminId;
  private TrustBooksFixture.Tenant books;
  private String i3Number;
  private String i4Number;
  private UUID i3;
  private UUID i4;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Position Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tpv_owner@test.com", "Position Owner", "owner"));
    adminId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, ADMIN, "tpv_admin@test.com", "Position Admin", "admin"));
    books = fixture.tenant(schema, ORG_ID, ownerId);

    // The investment accounts the §17.3.13 position names: I3 s86(3), I4 s86(4).
    i3Number = "I3-0001";
    i4Number = "I4-0001";
    i3 = trustAccount("TPV Investment I3", i3Number, "INVESTMENT", "FIRM_DISCRETION", "2025-01-01");
    i4 =
        trustAccount(
            "TPV Investment I4", i4Number, "INVESTMENT", "CLIENT_INSTRUCTION", "2025-01-01");

    // Clients A, B, C taken on with their previous-system ids …
    var clients =
        TakeOnTestApi.validatedClients(
            mockMvc,
            owner(),
            TakeOnClientsFiles.csv(
                TakeOnClientsFiles.row("Anele Client", "tpv.a@example.test").ref("A"),
                TakeOnClientsFiles.row("Bongani Client", "tpv.b@example.test").ref("B"),
                TakeOnClientsFiles.row("Chantal Client", "tpv.c@example.test").ref("C")),
            TakeOnClientsFiles.mapping());
    TakeOnTestApi.attest(mockMvc, owner(), clients.batchId(), clients.hash(), Map.of())
        .andExpect(status().isOk());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, clients.batchId(), clients.hash());

    // … and their matters A1, B1, C1 by reference number.
    var matters =
        TakeOnTestApi.validatedMatters(
            mockMvc,
            owner(),
            TakeOnMattersFiles.csv(
                TakeOnMattersFiles.row("Estate of A").clientRef("A").number("A1"),
                TakeOnMattersFiles.row("Transfer for B").clientRef("B").number("B1"),
                TakeOnMattersFiles.row("Claim for C").clientRef("C").number("C1")),
            TakeOnMattersFiles.mapping());
    assertThat((Integer) JsonPath.read(matters.json(), "$.counts.valid")).isEqualTo(3);
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, matters.batchId(), matters.hash());
  }

  @AfterAll
  void booksStayBalanced() {
    books.assertFirmBalanced();
  }

  // 1
  @Test
  void theSection17313PositionValidatesWithEveryDifferenceZero() throws Exception {
    UUID g = bankingAccount();
    UUID batch = uploadAndPosition(g, files(), section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("VALIDATED");
    assertThat((Integer) JsonPath.read(json, "$.counts.total")).isEqualTo(9);
    assertThat((Integer) JsonPath.read(json, "$.counts.error")).isZero();
    assertThat((Integer) JsonPath.read(json, "$.counts.blocks")).isZero();
    assertThat((List<String>) JsonPath.read(json, "$.allowedActions")).doesNotContain("COMMIT");
    assertThat((List<?>) JsonPath.read(json, "$.summary.accountErrors")).isEmpty();
    assertThat((List<?>) JsonPath.read(json, "$.summary.blocks")).isEmpty();
    assertThat((List<?>) JsonPath.read(json, "$.summary.warnings")).isEmpty();
    assertThat((List<String>) JsonPath.read(json, "$.summary.flags"))
        .containsExactly("UNIDENTIFIED_OVER_ONE_YEAR");
    assertThat((Map<String, Object>) JsonPath.read(json, "$.summary.equation.eq1"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "bankBalance", "418500.00",
                "plusOutstandingDeposits", "5000.00",
                "lessOutstandingPayments", "20000.00",
                "adjustedBank", "403500.00",
                "cashbook", "403500.00",
                "difference", "0.00",
                "holds", true));
    assertThat((Map<String, Object>) JsonPath.read(json, "$.summary.equation.itemsTie"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "listedDeposits", "5000.00",
                "listedPayments", "20000.00",
                "outstandingDeposits", "5000.00",
                "outstandingPayments", "20000.00",
                "difference", "0.00",
                "holds", true));
    assertThat((Map<String, Object>) JsonPath.read(json, "$.summary.equation.eq2"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "clientLedgers", "499500.00",
                "fundLedger", "1250.00",
                "unidentified", "2750.00",
                "placedInSection86_3", "-100000.00",
                "creditors", "403500.00",
                "cashbook", "403500.00",
                "difference", "0.00",
                "holds", true));
    assertThat((Map<String, Object>) JsonPath.read(json, "$.summary.equation.investments"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "perConfirmations",
                "300000.00",
                "listed",
                "300000.00",
                "difference",
                "0.00",
                "holds",
                true));
    assertThat((Map<String, Object>) JsonPath.read(json, "$.summary.equation.deficit"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "trustCreditors",
                "703500.00",
                "moneyHeld",
                "703500.00",
                "difference",
                "0.00",
                "inDeficit",
                false));
    // the position inputs come back on the batch
    assertThat((Boolean) JsonPath.read(json, "$.midMonthAcknowledged")).isFalse();
    assertThat(new BigDecimal(JsonPath.read(json, "$.position.bankBalance").toString()))
        .isEqualByComparingTo("418500.00");
    assertThat(new BigDecimal(JsonPath.read(json, "$.position.investedTotal").toString()))
        .isEqualByComparingTo("300000.00");

    // the derived ids, and the over-one-year flag by the ORIGINAL date (15 June 2025 only)
    var byRow = rowsBySection(batch);
    var a = byRow.get(CLIENT_LEDGER + ":2");
    assertThat(values(a))
        .containsKeys("clientId", "matterId")
        .containsEntry("clientResolvedBy", "previousSystemId");
    assertThat(values(byRow.get(INVESTMENTS + ":3")))
        .containsEntry("investmentAccountId", i4.toString());
    assertThat(warnings(byRow.get(UNIDENTIFIED_DEPOSITS + ":2")))
        .extracting(w -> w.get("code"))
        .containsExactly("UNIDENTIFIED_OVER_ONE_YEAR");
    assertThat(warnings(byRow.get(UNIDENTIFIED_DEPOSITS + ":3"))).isEmpty();

    // the validated audit event says whether EQ-1 and EQ-2 held (booleans, no amounts)
    var validated =
        TakeOnTestApi.auditEvents(jdbc, schema, batch).stream()
            .filter(e -> e.eventType().equals("takeon.batch.validated"))
            .toList()
            .getLast();
    assertThat(validated.details())
        .containsEntry("eq1_holds", true)
        .containsEntry("eq2_holds", true);

    // nothing posted: no take-on row, no investment, no unidentified item on these accounts
    assertThat(
            count(
                "SELECT count(*) FROM %s.trust_transactions WHERE trust_account_id IN (?, ?, ?)",
                g, i3, i4))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM %s.trust_investments WHERE trust_account_id IN (?, ?, ?)",
                g, i3, i4))
        .isZero();
    assertThat(count("SELECT count(*) FROM %s.unidentified_deposits WHERE trust_account_id = ?", g))
        .isZero();
  }

  // 2
  @Test
  void aClientInDebitIsABlock_negativeAndDr() throws Exception {
    UUID g = bankingAccount();
    for (String debit : List.of("-500.00", "500.00DR")) {
      var files =
          withRows(
              files(),
              CLIENT_LEDGER,
              List.of("A,A1,300000.00,", "B,B1,150000.00,", "C,C1," + debit + ","));
      UUID batch = uploadAndPosition(g, files, section17313Position(C));
      String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

      var row = rowsBySection(batch).get(CLIENT_LEDGER + ":4");
      assertThat(row.get("block")).as(debit).isEqualTo(true);
      assertError(
          row,
          "CLIENT_IN_DEBIT",
          "balance",
          "This client's matter is in debit (R-500,00) at the cutover date. No trust creditor may"
              + " be in debit (Rule 54.14.9). The firm must immediately report a debit balance to"
              + " the Council in writing, with the reason and proof that it was rectified (Rule"
              + " 54.14.11). Correct it in the previous system, then upload the corrected file.");
      assertThat(rowContent(json(rows(mockMvc, owner(), batch, "blocksOnly=true"))))
          .extracting(r -> r.get("id"))
          .containsExactly(row.get("id"));
      // EQ-2 fails too: the creditors are R50 000 below the cashbook
      assertThat((String) JsonPath.read(json, "$.summary.equation.eq2.creditors"))
          .isEqualTo("353500.00");
      assertThat((List<String>) JsonPath.read(json, "$.summary.blocks[*].code"))
          .containsExactly("CONTROL_TOTAL_EQ2");
      assertThat((String) JsonPath.read(json, "$.summary.blocks[0].message"))
          .contains("difference R50" + NBSP + "000,00.");
      assertThat((Integer) JsonPath.read(json, "$.counts.blocks")).isEqualTo(2); // row + EQ-2
      assertThat((List<String>) JsonPath.read(json, "$.allowedActions")).doesNotContain("COMMIT");
    }
  }

  // 3, 4
  @Test
  void aCashbookOffByTenRandIsTheControlTotalBlock_andTheCorrectedFigureValidatesClean()
      throws Exception {
    UUID g = bankingAccount();
    UUID batch =
        uploadAndPosition(
            g, files(), withFigure(section17313Position(C), "cashbookBalance", "403510.00"));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    assertThat((List<String>) JsonPath.read(json, "$.summary.blocks[*].code"))
        .containsExactly("CONTROL_TOTAL_EQ1", "CONTROL_TOTAL_EQ2");
    assertThat((String) JsonPath.read(json, "$.summary.blocks[0].message"))
        .isEqualTo(
            "Bank balance R418 500,00 + outstanding deposits R5 000,00 −"
                + " outstanding payments R20 000,00 is R403 500,00, not the cashbook"
                + " balance R403 510,00.");
    assertThat((String) JsonPath.read(json, "$.summary.blocks[1].message"))
        .isEqualTo(
            TrustPositionMessages.eq2(new BigDecimal("403500.00"), new BigDecimal("403510.00")))
        .contains("difference R10,00.");
    assertThat((String) JsonPath.read(json, "$.summary.equation.eq1.difference"))
        .isEqualTo("-10.00");
    assertThat((String) JsonPath.read(json, "$.summary.equation.eq2.difference"))
        .isEqualTo("-10.00");
    assertThat((Boolean) JsonPath.read(json, "$.summary.equation.deficit.inDeficit")).isFalse();
    assertThat((Integer) JsonPath.read(json, "$.counts.blocks")).isEqualTo(2); // no row block

    // the corrected figure: the batch returns to MAPPED (no summary) and validates clean
    String corrected =
        json(position(mockMvc, owner(), batch, section17313Position(C)).andExpect(status().isOk()));
    assertThat((String) JsonPath.read(corrected, "$.status")).isEqualTo("MAPPED");
    assertThat((Object) JsonPath.read(corrected, "$.summary")).isNull();
    String clean = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    assertThat((List<String>) JsonPath.read(clean, "$.summary.equation[*].difference"))
        .containsOnly("0.00")
        .hasSize(5);
    assertThat((Integer) JsonPath.read(clean, "$.counts.blocks")).isZero();
  }

  // 5
  @Test
  void aDeficitIsShownFirst() throws Exception {
    var position =
        withFigure(
            withFigure(section17313Position(C), "bankBalance", "408500.00"),
            "cashbookBalance",
            "393500.00");
    UUID batch = uploadAndPosition(bankingAccount(), files(), position);
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    assertThat((List<String>) JsonPath.read(json, "$.summary.blocks[*].code"))
        .containsExactly("TRUST_DEFICIT", "CONTROL_TOTAL_EQ2");
    assertThat((String) JsonPath.read(json, "$.summary.blocks[0].message"))
        .isEqualTo(
            ("The trust creditors (R703 500,00) exceed the trust money held (R693 500,00) by"
                    + " R10 000,00 at the cutover date. This is a trust deficit (Rules 54.14.8 and"
                    + " 54.14.10). Take-on cannot continue until it is resolved in the previous"
                    + " system.")
                .replace("R703 ", "R703" + NBSP)
                .replace("R693 ", "R693" + NBSP)
                .replace("R10 ", "R10" + NBSP));
    assertThat((Boolean) JsonPath.read(json, "$.summary.equation.deficit.inDeficit")).isTrue();
    assertThat((Boolean) JsonPath.read(json, "$.summary.equation.eq1.holds")).isTrue();
  }

  // 6
  @Test
  void theItemsTieBlock() throws Exception {
    // OD 6 000 with the bank at 417 500 keeps EQ-1 (417 500 + 6 000 − 20 000 = 403 500)
    var position =
        withFigure(
            withFigure(section17313Position(C), "outstandingDepositsTotal", "6000.00"),
            "bankBalance",
            "417500.00");
    UUID batch = uploadAndPosition(bankingAccount(), files(), position);
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    assertThat((List<String>) JsonPath.read(json, "$.summary.blocks[*].code"))
        .containsExactly("CONTROL_TOTAL_DEPOSITS");
    assertThat((String) JsonPath.read(json, "$.summary.blocks[0].message"))
        .isEqualTo(
            "The outstanding deposits listed total R5"
                + NBSP
                + "000,00; the reconciled position says R6"
                + NBSP
                + "000,00.");
    assertThat((Boolean) JsonPath.read(json, "$.summary.equation.itemsTie.holds")).isFalse();
    assertThat((String) JsonPath.read(json, "$.summary.equation.itemsTie.difference"))
        .isEqualTo("1000.00");
    assertThat((Integer) JsonPath.read(json, "$.counts.blocks")).isEqualTo(1);
  }

  // 7
  @Test
  void anUnidentifiedDepositWithoutItsDateIsABlock() throws Exception {
    var files =
        withRows(
            files(),
            UNIDENTIFIED_DEPOSITS,
            List.of(",2000.00,ACB CREDIT UNKNOWN,", "someday,750.00,EFT 7781,"));
    UUID batch = uploadAndPosition(bankingAccount(), files, section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    var byRow = rowsBySection(batch);
    for (String key : List.of(UNIDENTIFIED_DEPOSITS + ":2", UNIDENTIFIED_DEPOSITS + ":3")) {
      var row = byRow.get(key);
      assertThat(row.get("block")).as(key).isEqualTo(true);
      assertThat(errors(row))
          .as("the block replaces the typing error on the date")
          .extracting(e -> e.get("code"))
          .containsExactly("UNIDENTIFIED_DATE_MISSING");
      assertError(
          row,
          "UNIDENTIFIED_DATE_MISSING",
          "receivedDate",
          "This unidentified deposit has no original deposit date. The date the money was"
              + " received must be kept: the period after which unclaimed money is paid to the"
              + " Legal Practitioners' Fidelity Fund runs from it (section 87(4)).");
    }
    // the amounts still count: the equation agrees, only the two rows block
    assertThat((List<?>) JsonPath.read(json, "$.summary.blocks")).isEmpty();
    assertThat((Integer) JsonPath.read(json, "$.counts.blocks")).isEqualTo(2);
  }

  // 8
  @Test
  void aClientRowOnAnotherClientsMatterIsABlock_asIsABlankOrUnknownMatter() throws Exception {
    var files =
        withRows(
            files(),
            CLIENT_LEDGER,
            List.of("A,A1,300000.00,", "B,A1,150000.00,", "C,,49500.00,", "A,ZZ-404,0.00,"));
    UUID batch = uploadAndPosition(bankingAccount(), files, section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    var byRow = rowsBySection(batch);
    assertThat(byRow.get(CLIENT_LEDGER + ":2").get("status")).isEqualTo("VALIDATED");
    var mismatch = byRow.get(CLIENT_LEDGER + ":3");
    assertThat(mismatch.get("block")).isEqualTo(true);
    assertError(
        mismatch,
        "CLIENT_MATTER_MISMATCH",
        "matterRef",
        "Matter A1 is not a matter of client B. Every client balance must sit on one of that"
            + " client's matters.");
    var blank = byRow.get(CLIENT_LEDGER + ":4");
    assertThat(blank.get("block")).isEqualTo(true);
    assertThat(errors(blank))
        .extracting(e -> e.get("code"))
        .containsExactly("CLIENT_MATTER_REQUIRED");
    assertError(
        blank,
        "CLIENT_MATTER_REQUIRED",
        "matterRef",
        "Enter the matter of client C. Every client balance must sit on one of that client's"
            + " matters.");
    var unknown = byRow.get(CLIENT_LEDGER + ":5");
    assertThat(unknown.get("block")).isEqualTo(true);
    assertError(
        unknown,
        "CLIENT_MATTER_UNRESOLVED",
        "matterRef",
        "No matter matches ZZ-404. Take the matter on first, or correct the reference.");
    assertThat((Integer) JsonPath.read(json, "$.counts.blocks")).isEqualTo(3);
  }

  // 9
  @Test
  void aFundLedgerInDebitIsFlaggedNotBlocked() throws Exception {
    var position =
        withFigure(
            withFigure(
                withFigure(section17313Position(C), "fundLedgerBalance", "-1250.00"),
                "bankBalance",
                "416000.00"),
            "cashbookBalance",
            "401000.00");
    UUID batch = uploadAndPosition(bankingAccount(), files(), position);
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    assertThat((List<String>) JsonPath.read(json, "$.summary.flags"))
        .containsExactly("FUND_LEDGER_IN_DEBIT", "UNIDENTIFIED_OVER_ONE_YEAR");
    assertThat((String) JsonPath.read(json, "$.summary.warnings[0].code"))
        .isEqualTo("FUND_LEDGER_IN_DEBIT");
    assertThat((String) JsonPath.read(json, "$.summary.warnings[0].message"))
        .isEqualTo(
            "The Legal Practitioners' Fidelity Fund interest and charges ledger is in debit"
                + " (R-1 250,00) at the cutover date. This is allowed and is flagged, as it"
                + " would be after take-on.");
    assertThat((List<?>) JsonPath.read(json, "$.summary.blocks")).isEmpty();
    assertThat((Integer) JsonPath.read(json, "$.counts.blocks")).isZero();
    assertThat((Boolean) JsonPath.read(json, "$.summary.equation.deficit.inDeficit")).isFalse();
  }

  // 10
  @Test
  void aClosedAccountIsRefusedAboveTheTable_andTheRowsGetTheGenericChecksOnly() throws Exception {
    UUID g = bankingAccount();
    String name = accountName(g);
    mockMvc
        .perform(post("/api/trust-accounts/" + g + "/close").with(owner()))
        .andExpect(status().isOk());
    var files =
        withRows(
            files(),
            CLIENT_LEDGER,
            List.of("A,A1,300000.00,", "B,A1,150000.00,", "C,C1,-49500.00,"));
    UUID batch = uploadAndPosition(g, files, section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("VALIDATED");
    assertThat((List<String>) JsonPath.read(json, "$.summary.accountErrors[*].code"))
        .containsExactly("ACCOUNT_CLOSED");
    assertThat((String) JsonPath.read(json, "$.summary.accountErrors[0].message"))
        .isEqualTo(name + " is closed. Take on only into open trust accounts.");
    assertThat((Object) JsonPath.read(json, "$.summary.equation")).isNull();
    assertThat((Integer) JsonPath.read(json, "$.counts.blocks"))
        .as("stopping further checks: no row block, no equation")
        .isZero();
    assertThat(rowsBySection(batch).values())
        .allSatisfy(r -> assertThat(values(r)).doesNotContainKey("clientId"));
  }

  // 11
  @Test
  void anOpenedDateAfterTheCutoverIsRefused() throws Exception {
    UUID g = bankingAccount("2026-11-15");
    UUID batch = uploadAndPosition(g, files(), section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    assertAccountError(
        json,
        "ACCOUNT_OPENED_AFTER_CUTOVER",
        "The account's opened date (15 November 2026) is after the cutover date (31 October 2026)."
            + " Change the opened date in Trust accounting before taking on.");
  }

  // 12
  @Test
  void rowsOnOrBeforeTheCutoverAreRefused_butARejectedRowIsNot() throws Exception {
    var client = books.newClient("TPV");
    UUID g = bankingAccount();
    books.deposit(g, client, "100.00", CUTOVER.minusDays(1));
    UUID batch = uploadAndPosition(g, files(), section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    assertAccountError(
        json,
        "ACCOUNT_HAS_EARLIER_ENTRIES",
        "This account already has transactions dated on or before the cutover date. A take-on"
            + " must be the first entry.");

    UUID rejectedOnly = bankingAccount();
    UUID payment = books.requestPayment(rejectedOnly, client, "100.00", CUTOVER.minusDays(1));
    books.reject(rejectedOnly, payment, adminId);
    UUID passes = uploadAndPosition(rejectedOnly, files(), section17313Position(C));
    String ok = json(validate(mockMvc, owner(), passes).andExpect(status().isOk()));
    assertThat((List<?>) JsonPath.read(ok, "$.summary.accountErrors")).isEmpty();
  }

  // 13
  @Test
  void aCompletedReconciliationIsRefused() throws Exception {
    UUID g = bankingAccount();
    completeReconciliation(g);
    UUID batch = uploadAndPosition(g, files(), section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    assertAccountError(
        json, "ACCOUNT_RECONCILED", "This account already has a completed reconciliation.");
  }

  // 14
  @Test
  void anAccountAlreadyTakenOnIsRefused() throws Exception {
    UUID g = bankingAccount();
    UUID earlier = books.takeOnBatch(g, CUTOVER);
    books.markTakenOn(g, CUTOVER, earlier);
    UUID batch = uploadAndPosition(g, files(), section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    assertAccountError(
        json,
        "ACCOUNT_ALREADY_TAKEN_ON",
        "This account has already been taken on (batch " + earlier + ").");
  }

  // 15
  @Test
  void aSharedInvestmentAccount_sameCutoverPasses_otherCutoverIsRefused() throws Exception {
    // Another cutover date: the investment account was taken on at 30 September 2026.
    UUID other = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    LocalDate september = LocalDate.of(2026, 9, 30);
    books.markTakenOn(other, september, books.takeOnBatch(books.freshAccount(false), september));
    UUID batch =
        uploadAndPosition(
            bankingAccount(),
            withRows(
                files(),
                INVESTMENTS,
                TakeOnTrustFiles.section17313Investments(i3Number, accountNumber(other))),
            section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    assertAccountError(
        json,
        "INVESTMENT_TAKEN_ON_AT_OTHER_DATE",
        accountName(other)
            + " was taken on at 30 September 2026; take this account on at the"
            + " same date.");

    // The same cutover date: another banking account's COMPLETED take-on already posted its
    // opening rows on the shared s86(4) account at C (the 657A harness, postOpening) — allowed.
    var world = books.takeOnSection17313(CUTOVER);
    books.completeTakeOnBatch(world.batch());
    assertThat(
            count(
                "SELECT count(*) FROM %s.trust_transactions WHERE trust_account_id = ?"
                    + " AND transaction_date = ? AND take_on_batch_id = ?",
                world.i4(), CUTOVER, world.batch()))
        .as("the shared account already holds the other batch's opening rows at C")
        .isPositive();
    UUID shared =
        uploadAndPosition(
            bankingAccount(),
            withRows(
                files(),
                INVESTMENTS,
                TakeOnTrustFiles.section17313Investments(i3Number, accountNumber(world.i4()))),
            section17313Position(C));
    String ok = json(validate(mockMvc, owner(), shared).andExpect(status().isOk()));
    assertThat((List<?>) JsonPath.read(ok, "$.summary.accountErrors")).isEmpty();
    assertThat((Integer) JsonPath.read(ok, "$.counts.blocks")).isZero();
    assertThat((Integer) JsonPath.read(ok, "$.counts.error")).isZero();
  }

  // 16
  @Test
  void aMidMonthCutoverNeedsTheAcknowledgement() throws Exception {
    UUID g = bankingAccount();
    var midMonth = withFigure(section17313Position(C), "cutoverDate", "2026-10-15");
    UUID batch = uploadAndPosition(g, files(), midMonth);
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    assertAccountError(
        json,
        "CUTOVER_NOT_MONTH_END",
        "The cutover date is not a month end. Confirm a mid-month cutover, and reconcile the"
            + " previous system to this date.");

    String acknowledged =
        json(
            position(mockMvc, owner(), batch, withFigure(midMonth, "midMonthAcknowledged", true))
                .andExpect(status().isOk()));
    assertThat((Boolean) JsonPath.read(acknowledged, "$.midMonthAcknowledged")).isTrue();
    String ok = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    assertThat((List<?>) JsonPath.read(ok, "$.summary.accountErrors")).isEmpty();
  }

  // 17
  @Test
  void nilBalancesAreValidatedAndListed() throws Exception {
    // C/C1 at R0,00: the cashbook and bank move by −49 500 so the equation still agrees
    var files =
        withRows(
            files(), CLIENT_LEDGER, List.of("A,A1,300000.00,", "B,B1,150000.00,", "C,C1,0.00,"));
    var position =
        withFigure(
            withFigure(section17313Position(C), "cashbookBalance", "354000.00"),
            "bankBalance",
            "369000.00");
    UUID batch = uploadAndPosition(bankingAccount(), files, position);
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    var nil = rowsBySection(batch).get(CLIENT_LEDGER + ":4");
    assertThat(nil.get("status")).isEqualTo("VALIDATED");
    assertThat(errors(nil)).isEmpty();
    assertThat(warnings(nil)).isEmpty();
    assertThat(values(nil)).containsEntry("balance", "0.00");
    assertThat((Integer) JsonPath.read(json, "$.counts.blocks")).isZero();
    assertThat((Integer) JsonPath.read(json, "$.counts.error")).isZero();
  }

  // 18
  @Test
  void aSection864InvestmentWithoutItsInstructionIsAnErrorNotABlock() throws Exception {
    var files =
        withRows(
            files(),
            INVESTMENTS,
            List.of(
                TakeOnTrustFiles.section863(i3Number, "100000.00", "100000.00"),
                TakeOnTrustFiles.section864(i4Number, "")));
    UUID batch = uploadAndPosition(bankingAccount(), files, section17313Position(C));
    validate(mockMvc, owner(), batch).andExpect(status().isOk());

    var row = rowsBySection(batch).get(INVESTMENTS + ":3");
    assertThat(row.get("block")).isEqualTo(false);
    assertError(
        row,
        "INSTRUCTION_REQUIRED",
        "instructionReference",
        "A section 86(4) investment needs the client's instruction reference, its date and"
            + " confirmation that the account is endorsed under section 86(4) (Rule 54.17.3).");
  }

  // 19
  @Test
  void anInvestmentMovementInTransitIsRefused() throws Exception {
    var files =
        withRows(
            files(),
            OUTSTANDING_ITEMS,
            List.of(
                "Deposit,30/10/2026,5000.00,EFT B-0001,B,B1",
                "Cheque,28/10/2026,20000.00,PAY-778,A,A1",
                "Investment,29/10/2026,1000.00,INV-MOVE-1,A,A1"));
    UUID batch = uploadAndPosition(bankingAccount(), files, section17313Position(C));
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));

    assertError(
        rowsBySection(batch).get(OUTSTANDING_ITEMS + ":4"),
        "INVESTMENT_IN_TRANSIT",
        "kind",
        "Settle investment movements in the previous system before the cutover date.");
    assertThat((String) JsonPath.read(json, "$.summary.equation.itemsTie.difference"))
        .as("neither a deposit nor a payment: the listed totals are unchanged")
        .isEqualTo("0.00");
  }

  // 20
  @Test
  void positionInputs_partOfTheHash_andAChangeReturnsTheBatchToMapped() throws Exception {
    UUID g = bankingAccount();
    UUID batch = uploadAndPosition(g, files(), section17313Position(C));
    String first = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    String h1 = JsonPath.read(first, "$.validationHash");

    // the same figures (another scale) are no change
    var sameScale = withFigure(section17313Position(C), "fundLedgerBalance", "1250");
    String same = json(position(mockMvc, owner(), batch, sameScale).andExpect(status().isOk()));
    assertThat((String) JsonPath.read(same, "$.status")).isEqualTo("VALIDATED");
    assertThat((String) JsonPath.read(same, "$.validationHash")).isEqualTo(h1);

    // the date alone keeps the stored figures (a null figure is "unchanged")
    String dateOnly =
        json(
            position(mockMvc, owner(), batch, Map.of("cutoverDate", C)).andExpect(status().isOk()));
    assertThat((String) JsonPath.read(dateOnly, "$.status")).isEqualTo("VALIDATED");
    assertThat(new BigDecimal(JsonPath.read(dateOnly, "$.position.cashbookBalance").toString()))
        .isEqualByComparingTo("403500.00");

    // a changed Fund figure returns the batch to MAPPED, and the next dry run has another hash
    var changed = withFigure(section17313Position(C), "fundLedgerBalance", "1300.00");
    String moved = json(position(mockMvc, owner(), batch, changed).andExpect(status().isOk()));
    assertThat((String) JsonPath.read(moved, "$.status")).isEqualTo("MAPPED");
    String second = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    assertThat((String) JsonPath.read(second, "$.validationHash")).isNotEqualTo(h1);

    // negative outstanding deposits: 400, and nothing stored
    position(
            mockMvc,
            owner(),
            batch,
            withFigure(section17313Position(C), "outstandingDepositsTotal", "-1.00"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Outstanding deposits, outstanding payments and the total invested cannot be"
                        + " negative."));
    String unchanged = json(detail(mockMvc, owner(), batch));
    assertThat(
            new BigDecimal(
                JsonPath.read(unchanged, "$.position.outstandingDepositsTotal").toString()))
        .isEqualByComparingTo("5000.00");
    assertThat((String) JsonPath.read(unchanged, "$.status")).isEqualTo("VALIDATED");

    // more than two decimals: 400
    position(
            mockMvc,
            owner(),
            batch,
            withFigure(section17313Position(C), "bankBalance", "418500.001"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Enter amounts in rand and cents, up to R9 999 999 999 999,99."));

    // a trust batch without its four figures cannot be validated: 409
    UUID bare = upload(bankingAccount(), files());
    position(mockMvc, owner(), bare, Map.of("cutoverDate", C)).andExpect(status().isOk());
    validate(mockMvc, owner(), bare)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_POSITION_REQUIRED"))
        .andExpect(
            jsonPath("$.detail")
                .value("Enter the reconciled position at cutover before validating."));

    // a debtor take-on has no trust position: 400
    UUID[] debtors =
        create(
            mockMvc,
            owner(),
            "DEBTOR_BALANCES",
            "debtors.csv",
            TakeOnTestFiles.csv("Client,Fee note,Issued,Outstanding\nA,FN-1,01/10/2026,100.00\n"));
    position(mockMvc, owner(), debtors[0], withFigure(Map.of("cutoverDate", C), "bankBalance", 1))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("The trust position applies only to a trust take-on."));
    String debtorJson =
        json(
            position(mockMvc, owner(), debtors[0], Map.of("cutoverDate", C))
                .andExpect(status().isOk()));
    assertThat((Object) JsonPath.read(debtorJson, "$.position")).isNull();
  }

  // --- helpers ---

  private Map<String, List<String>> files() {
    return section17313(i3Number, i4Number);
  }

  /** Uploads every section of {@code files} into a new trust batch on {@code account}, maps it. */
  private UUID upload(UUID account, Map<String, List<String>> files) throws Exception {
    UUID batch = null;
    for (String section : TakeOnTrustFiles.SECTIONS) {
      var rows = files.get(section);
      if (rows == null || rows.isEmpty()) {
        continue;
      }
      byte[] bytes = TakeOnTestFiles.csv(TakeOnTrustFiles.csv(section, rows));
      String fileName = section.toLowerCase(java.util.Locale.ROOT) + ".csv";
      UUID source;
      if (batch == null) {
        UUID[] ids =
            create(
                mockMvc,
                owner(),
                "TRUST_POSITION",
                fileName,
                bytes,
                "section",
                section,
                "trustAccountId",
                account.toString());
        batch = ids[0];
        source = ids[1];
      } else {
        String added =
            json(
                addSource(mockMvc, owner(), batch, section, fileName, bytes)
                    .andExpect(status().isCreated()));
        source = UUID.fromString(JsonPath.read(added, "$.sourceId"));
      }
      map(mockMvc, owner(), batch, source, TakeOnTrustFiles.mapping(section))
          .andExpect(status().isOk());
    }
    return batch;
  }

  private UUID uploadAndPosition(
      UUID account, Map<String, List<String>> files, Map<String, Object> position)
      throws Exception {
    UUID batch = upload(account, files);
    position(mockMvc, owner(), batch, position).andExpect(status().isOk());
    return batch;
  }

  /** A fresh trust banking account opened on 1 January 2025. */
  private UUID bankingAccount() throws Exception {
    return bankingAccount("2025-01-01");
  }

  private UUID bankingAccount(String openedDate) throws Exception {
    int n = accounts.incrementAndGet();
    return trustAccount("TPV Trust " + n, "6200%07d".formatted(n), "GENERAL", null, openedDate);
  }

  private UUID trustAccount(
      String name, String number, String type, String basis, String openedDate) throws Exception {
    String basisField = basis == null ? "" : ", \"investmentBasis\": \"%s\"".formatted(basis);
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"accountName": "%s", "bankName": "First National Bank",
                         "branchCode": "250655", "accountNumber": "%s",
                         "accountType": "%s", "isPrimary": false,
                         "requireDualApproval": false, "openedDate": "%s"%s}
                        """
                            .formatted(name, number, type, openedDate, basisField)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(TestEntityHelper.extractId(result));
  }

  private String accountName(UUID account) {
    return books.read(() -> trustAccounts.findById(account).orElseThrow().getAccountName());
  }

  private String accountNumber(UUID account) {
    return books.read(() -> trustAccounts.findById(account).orElseThrow().getAccountNumber());
  }

  /**
   * A completed reconciliation on {@code account}, through the API (the {@code
   * TrustReconciliationControllerTest} path): one deposit after the cutover date, its statement,
   * auto-match, calculate, complete.
   */
  private void completeReconciliation(UUID account) throws Exception {
    UUID clientId = takenOnResult("CLIENTS", "A");
    UUID matterId = takenOnMatter("A1");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + account + "/transactions/deposit")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clientId": "%s", "matterId": "%s", "amount": 20000.00,
                     "reference": "DEP-TPV-REC", "description": "Deposit after cutover",
                     "transactionDate": "2026-11-02"}
                    """
                        .formatted(clientId, matterId)))
        .andExpect(status().isCreated());
    var csv =
        """
        FNB Trust Account Statement - Account %s
        Date,Description,Amount,Balance,Reference
        02/11/2026,Deposit received,20000.00,20000.00,DEP-TPV-REC
        """
            .formatted(accountNumber(account));
    String statementId =
        JsonPath.read(
                json(
                    mockMvc
                        .perform(
                            multipart("/api/trust-accounts/{accountId}/bank-statements", account)
                                .file(
                                    new MockMultipartFile(
                                        "file",
                                        "tpv-statement-" + account + ".csv",
                                        "text/csv",
                                        csv.getBytes(StandardCharsets.UTF_8)))
                                .with(owner()))
                        .andExpect(status().isCreated())),
                "$.id")
            .toString();
    mockMvc
        .perform(post("/api/bank-statements/{id}/auto-match", statementId).with(owner()))
        .andExpect(status().isOk());
    String reconciliationId =
        JsonPath.read(
                json(
                    mockMvc
                        .perform(
                            post("/api/trust-accounts/{accountId}/reconciliations", account)
                                .with(owner())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {"periodEnd": "2026-11-02", "bankStatementId": "%s"}
                                    """
                                        .formatted(statementId)))
                        .andExpect(status().isCreated())),
                "$.id")
            .toString();
    String expected = "{\"expectedBankStatementId\": \"%s\"}".formatted(statementId);
    String calculated =
        json(
            mockMvc
                .perform(
                    post("/api/trust-reconciliations/{id}/calculate", reconciliationId)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expected))
                .andExpect(status().isOk()));
    assertThat((Boolean) JsonPath.read(calculated, "$.isBalanced")).isTrue();
    mockMvc
        .perform(
            post("/api/trust-reconciliations/{id}/complete", reconciliationId)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(expected))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  /** The record a take-on row of {@code dataset} created, by its previous-system id (read-only). */
  private UUID takenOnResult(String dataset, String sourceRef) {
    return jdbc.queryForObject(
        ("SELECT result_entity_id FROM \"%s\".take_on_rows WHERE dataset = ? AND source_ref = ?"
                + " AND status = 'CREATED'")
            .formatted(schema),
        UUID.class,
        dataset,
        sourceRef);
  }

  private UUID takenOnMatter(String referenceNumber) {
    return jdbc.queryForObject(
        "SELECT id FROM \"%s\".projects WHERE reference_number = ?".formatted(schema),
        UUID.class,
        referenceNumber);
  }

  /** {@code section:rowNumber} → row. */
  private Map<String, Map<String, Object>> rowsBySection(UUID batch) throws Exception {
    return rowContent(json(rows(mockMvc, owner(), batch, "size=200"))).stream()
        .collect(Collectors.toMap(r -> r.get("section") + ":" + r.get("rowNumber"), r -> r));
  }

  private static void assertAccountError(String json, String code, String message) {
    assertThat((List<String>) JsonPath.read(json, "$.summary.accountErrors[*].code"))
        .containsExactly(code);
    assertThat((String) JsonPath.read(json, "$.summary.accountErrors[0].message"))
        .isEqualTo(message);
    assertThat((Object) JsonPath.read(json, "$.summary.equation")).isNull();
  }

  private static void assertError(
      Map<String, Object> row, String code, String field, String message) {
    assertThat(row.get("status")).isEqualTo("ERROR");
    assertThat(errors(row))
        .anySatisfy(
            e -> {
              assertThat(e.get("code")).isEqualTo(code);
              assertThat(e.get("field")).isEqualTo(field);
              assertThat(e.get("message")).isEqualTo(message);
            });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> values(Map<String, Object> row) {
    return (Map<String, Object>) row.get("values");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> errors(Map<String, Object> row) {
    return (List<Map<String, Object>>) row.get("errors");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> warnings(Map<String, Object> row) {
    return (List<Map<String, Object>>) row.get("warnings");
  }

  private long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql.formatted("\"" + schema + "\""), Long.class, args);
    return n == null ? 0 : n;
  }

  private static String json(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
