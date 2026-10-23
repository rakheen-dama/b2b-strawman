package io.b2mash.b2b.b2bstrawman.customer;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createCustomerWithStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientListRow;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.BillingSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.testutil.TenantTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustBalanceFactory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.view.SavedView;
import io.b2mash.b2b.b2bstrawman.view.SavedViewRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/clients} → {@code List<ClientListRow>} (Phase 87, Epic 615B): row shape,
 * capability redaction, the {@code ficaStatus} / {@code hasTrustBalance} / {@code showIncomplete}
 * params on top of the pre-existing view / lifecycleStatus filters, and the statement cap (≤ 10 for
 * a ≥ 50-client page, equal to a 1-client page; Hibernate statistics enabled in
 * application-test.yml). Assertions are id-scoped — the tenant is shared across methods.
 *
 * <p>Fixture (dedicated org, module trust_accounting):
 *
 * <ul>
 *   <li><b>A</b> (ACTIVE): FICA DONE; SENT fee notes 1,000.00 + 500.00 in the org currency, a large
 *       SENT one in a foreign currency (not the reported group), a legacy SENT one whose due date
 *       is nulled after approve (pre-V4 shape) and a DRAFT one (both excluded); 15,000.00 in trust;
 *       two ACTIVE matters + one ARCHIVED; a tag.
 *   <li><b>B</b> (ACTIVE): FICA IN_PROGRESS (unsent request); nothing else.
 *   <li><b>C</b> (PROSPECT): FICA NOT_STARTED; one SENT fee note in the foreign currency only.
 *   <li><b>D</b> (DORMANT, the only DORMANT client): both fixture-defined required CUSTOMER fields
 *       filled (100%; the others score below 100), FICA DONE, a tag and a ledger card — the 1-row
 *       page of the statement-count test.
 *   <li><b>E</b> (ONBOARDING/ACTIVE via Start Onboarding): FICA DONE through a completed onboarding
 *       checklist — no FICA request (ADR-348, the real service path).
 *   <li>50 bulk ACTIVE clients (reach the 50-client page).
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientListRowIntegrationTest {
  private static final String ORG_ID = "org_client_list_row";
  private static final String OWNER = "user_clr_owner";
  private static final String MEMBER = "user_clr_member";
  private static final String FIN = "user_clr_fin";
  private static final String BARE = "user_clr_bare";
  private static final String PACK_ID = "fica-onboarding-pack";
  private static final int CLIENT_PAGE = 50;
  private static final int STATEMENT_CAP = 10;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private ClientLedgerCardRepository clientLedgerCardRepository;
  @Autowired private SavedViewRepository savedViewRepository;
  @Autowired private EntityTagService entityTagService;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private final LocalDate today = LocalDate.now();

  private String schema;
  private String currency;
  private String foreignCurrency;
  private String ficaTemplateId;
  private UUID ownerId;
  private UUID tagId;
  private UUID clientA;
  private UUID legacyNoDueDateInvoiceId;
  private UUID clientB;
  private UUID clientC;
  private UUID clientD;
  private UUID clientE;
  private UUID bulkClient;
  private int invoiceSeq;

  @BeforeAll
  void setup() throws Exception {
    schema = provisioningService.provisionTenant(ORG_ID, "Client List Row Org", null).schemaName();
    ownerId = sync(OWNER, "CLR Owner", "owner");
    sync(MEMBER, "CLR Member", "member");
    UUID finId = sync(FIN, "CLR Finance", "member");
    assignRole(finId, createCustomRole("CLR Finance Role", "FINANCIAL_VISIBILITY"));
    // Holds neither FINANCIAL_VISIBILITY nor VIEW_TRUST.
    UUID bareId = sync(BARE, "CLR Bare", "member");
    assignRole(bareId, createCustomRole("CLR Bare Role", "GENERATE_STATEMENT_OF_ACCOUNT"));

    setTrustModule(true);
    asOwner(() -> currency = orgSettingsService.getDefaultCurrency());
    foreignCurrency = "EUR".equals(currency) ? "GBP" : "EUR";

    ficaTemplateId = createFicaTemplate();
    tagId =
        UUID.fromString(
            TestEntityHelper.createTag(
                mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), "CLR Priority", "#EF4444"));

    asOwner(this::seedEntities);

    // FICA: A and D DONE (completed, all items accepted); B IN_PROGRESS (unsent request).
    completeWithAllItemsAccepted(
        createFicaRequest(clientA, createPortalContact(clientA, "clr-a@test.com")));
    createFicaRequest(clientB, createPortalContact(clientB, "clr-b@test.com"));
    completeWithAllItemsAccepted(
        createFicaRequest(clientD, createPortalContact(clientD, "clr-d@test.com")));

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    // E: FICA DONE through the real onboarding path (ADR-348). The template is created after
    // seedEntities so the bulk clients (direct entities, never transitioned) get no instance.
    TestChecklistHelper.createOnboardingTemplate(mockMvc, ownerJwt, "CLR Onboarding");
    String e =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "CLR Echo Checklist Client", "clr_e@test.com");
    TestChecklistHelper.startOnboarding(mockMvc, e, ownerJwt);
    TestChecklistHelper.completeChecklistItems(mockMvc, e, ownerJwt);
    clientE = UUID.fromString(e);
  }

  private void seedEntities() {
    // Two required CUSTOMER fields (as CompletenessQueryTest does): every client but D scores <
    // 100.
    transactionTemplate.executeWithoutResult(
        tx -> {
          var taxNumber =
              new FieldDefinition(
                  EntityType.CUSTOMER, "CLR Tax Number", "clr_tax_number", FieldType.TEXT);
          taxNumber.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
          fieldDefinitionRepository.save(taxNumber);
          var address =
              new FieldDefinition(
                  EntityType.CUSTOMER, "CLR Address", "clr_address", FieldType.TEXT);
          address.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
          fieldDefinitionRepository.save(address);
        });
    var requiredDefs =
        fieldDefinitionRepository
            .findByEntityTypeAndActiveTrueOrderBySortOrder(EntityType.CUSTOMER)
            .stream()
            .filter(
                fd -> fd.getRequiredForContexts() != null && !fd.getRequiredForContexts().isEmpty())
            .toList();
    clientA = saveClient(createActiveCustomer("CLR Alpha Client", "clr_a@test.com", ownerId));
    clientB = saveClient(createActiveCustomer("CLR Bravo Client", "clr_b@test.com", ownerId));
    clientC =
        saveClient(
            createCustomerWithStatus(
                "CLR Charlie Client", "clr_c@test.com", ownerId, LifecycleStatus.PROSPECT));
    var complete =
        TestCustomerFactory.createActiveCustomerWithPrerequisites(
            "CLR Delta Client", "clr_d@test.com", ownerId, requiredDefs);
    complete.setLifecycleStatus(LifecycleStatus.DORMANT, ownerId);
    clientD = saveClient(complete);

    transactionTemplate.executeWithoutResult(
        tx -> {
          // A: two org-currency SENT fee notes count; the rest do not.
          seedSentInvoice(clientA, 1000, currency, today.plusDays(10));
          seedSentInvoice(clientA, 500, currency, today.minusDays(5));
          seedSentInvoice(clientA, 9999, foreignCurrency, today.plusDays(10));
          legacyNoDueDateInvoiceId =
              seedSentInvoice(clientA, 777, currency, null); // legacy SENT, no due date
          invoiceRepository.save(newInvoice(clientA, 4242, currency, today.plusDays(10))); // DRAFT
          // C: foreign currency only → the row reports that group.
          seedSentInvoice(clientC, 250, foreignCurrency, today.plusDays(3));

          TestTrustBalanceFactory.seedTrustBalance(
              trustAccountRepository,
              clientLedgerCardRepository,
              clientA,
              new BigDecimal("15000.00"));
          TestTrustBalanceFactory.seedTrustBalance(
              trustAccountRepository,
              clientLedgerCardRepository,
              clientD,
              new BigDecimal("200.00"));

          saveMatter("CLR Matter A1", clientA, false);
          saveMatter("CLR Matter A2", clientA, false);
          saveMatter("CLR Matter A3 archived", clientA, true);
        });
    // Legacy pre-V4 row: approved before approve() stamped a due date (ADR-347), shaped here by
    // nulling the stamped date. The debtor-book guard (CollectionsSql.OUTSTANDING_PREDICATE, 620B)
    // must still exclude it — this fixture documents the legacy guard. Runs after the seeding
    // transaction commits (a separate pooled connection would otherwise block on the uncommitted
    // row).
    jdbcTemplate.update(
        "UPDATE \"%s\".invoices SET due_date = NULL WHERE id = ?::uuid".formatted(schema),
        legacyNoDueDateInvoiceId.toString());
    // ADR-342: storage key, do not rename
    entityTagService.setEntityTags("CUSTOMER", clientA, List.of(tagId));
    // ADR-342: storage key, do not rename
    entityTagService.setEntityTags("CUSTOMER", clientD, List.of(tagId));

    for (int i = 0; i < CLIENT_PAGE; i++) {
      bulkClient =
          saveClient(createActiveCustomer("Bulk Client " + i, "bulk" + i + "@test.com", ownerId));
    }
  }

  // --- Row shape ---

  @Test
  void ownerSeesFullyPopulatedRow() throws Exception {
    var row = row(list(owner(), ""), clientA);

    assertThat(row)
        .containsEntry("name", "CLR Alpha Client")
        .containsEntry("email", "clr_a@test.com")
        .containsEntry("clientType", "INDIVIDUAL")
        .containsEntry("lifecycleStatus", "ACTIVE")
        .containsEntry("status", "ACTIVE")
        .containsEntry("ficaStatus", "DONE")
        // 1,000 + 500 in the org currency; the foreign, undated-SENT and DRAFT notes are excluded
        .containsEntry("outstandingFeeNoteCount", 2)
        .containsEntry("currency", currency)
        // two ACTIVE matters; the archived one is excluded
        .containsEntry("openMatterCount", 2)
        .containsKeys(
            "id", "phone", "createdAt", "lifecycleStatusChangedAt", "customFields", "tags");
    assertThat(row.get("ficaLastVerifiedAt"))
        .as("completed FICA request's completed_at")
        .isNotNull();
    assertThat(money(row, "outstandingFeeNoteTotal")).isEqualByComparingTo("1500.00");
    assertThat(money(row, "trustBalance")).isEqualByComparingTo("15000.00");
    @SuppressWarnings("unchecked")
    var tags = (List<Map<String, Object>>) row.get("tags");
    assertThat(tags).extracting(t -> t.get("id")).containsExactly(tagId.toString());

    // completenessPercent folds in the completeness-summary percentage
    var score = completenessSummary(clientA);
    assertThat(((Number) score.get("totalRequired")).intValue())
        .as("the fixture defines required CUSTOMER fields")
        .isPositive();
    assertThat(row).containsEntry("completenessPercent", score.get("percentage"));
  }

  @Test
  void rowsWithoutAggregatesCarryZerosAndStatusDefaults() throws Exception {
    var json = list(owner(), "");
    assertThat(row(json, clientB))
        .containsEntry("ficaStatus", "IN_PROGRESS")
        .containsEntry("ficaLastVerifiedAt", null)
        .containsEntry("outstandingFeeNoteCount", 0)
        .containsEntry("currency", currency)
        .containsEntry("openMatterCount", 0)
        .containsEntry("tags", List.of());
    assertThat(money(row(json, clientB), "outstandingFeeNoteTotal")).isEqualByComparingTo("0.00");
    // no ledger card → 0 for a caller who can see trust
    assertThat(money(row(json, clientB), "trustBalance")).isEqualByComparingTo("0");

    var c = row(json, clientC);
    assertThat(c)
        .containsEntry("ficaStatus", "NOT_STARTED")
        .containsEntry("lifecycleStatus", "PROSPECT")
        // only a foreign-currency group exists → that group is reported (D5)
        .containsEntry("currency", foreignCurrency)
        .containsEntry("outstandingFeeNoteCount", 1);
    assertThat(money(c, "outstandingFeeNoteTotal")).isEqualByComparingTo("250.00");
  }

  // --- Redaction by capability ---

  @Test
  void callerWithoutFinancialCapabilitiesGetsCountsButNoMoney() throws Exception {
    var row = row(list(TestJwtFactory.memberJwt(ORG_ID, BARE), ""), clientA);
    assertThat(row)
        .containsEntry("outstandingFeeNoteTotal", null)
        .containsEntry("trustBalance", null)
        .containsEntry("outstandingFeeNoteCount", 2)
        .containsEntry("currency", currency)
        .containsEntry("openMatterCount", 2)
        .containsEntry("ficaStatus", "DONE");
  }

  @Test
  void systemMemberRoleSeesTrustButNotFeeNoteTotal() throws Exception {
    // The system "member" role is seeded with VIEW_TRUST but not FINANCIAL_VISIBILITY (615A).
    var row = row(list(TestJwtFactory.memberJwt(ORG_ID, MEMBER), ""), clientA);
    assertThat(row)
        .containsEntry("outstandingFeeNoteTotal", null)
        .containsEntry("outstandingFeeNoteCount", 2);
    assertThat(money(row, "trustBalance")).isEqualByComparingTo("15000.00");
  }

  @Test
  void financialVisibilityShowsFeeNoteTotalButNotTrust() throws Exception {
    var row = row(list(TestJwtFactory.memberJwt(ORG_ID, FIN), ""), clientA);
    assertThat(money(row, "outstandingFeeNoteTotal")).isEqualByComparingTo("1500.00");
    assertThat(row).containsEntry("trustBalance", null).containsEntry("outstandingFeeNoteCount", 2);
  }

  // --- List params ---

  @Test
  void ficaStatusDoneAndOutstandingSplitTheClients() throws Exception {
    assertThat(ids(list(owner(), "?ficaStatus=DONE")))
        .contains(clientA.toString(), clientD.toString(), clientE.toString())
        .doesNotContain(clientB.toString(), clientC.toString(), bulkClient.toString());
    // case-insensitive; OUTSTANDING = NOT_STARTED or IN_PROGRESS
    assertThat(ids(list(owner(), "?ficaStatus=outstanding")))
        .contains(clientB.toString(), clientC.toString(), bulkClient.toString())
        .doesNotContain(clientA.toString(), clientD.toString(), clientE.toString());
  }

  @Test
  void checklistDrivenClientRowIsDone() throws Exception {
    var row = row(list(owner(), ""), clientE);
    assertThat(row).containsEntry("ficaStatus", "DONE");
    assertThat(row.get("ficaLastVerifiedAt"))
        .as("completed onboarding checklist's completed_at")
        .isNotNull();
  }

  @Test
  void invalidFicaStatusIsBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/clients?ficaStatus=bogus").with(owner()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid FICA status filter"))
        .andExpect(
            jsonPath("$.detail")
                .value("Invalid ficaStatus: 'bogus'. Valid values: OUTSTANDING, DONE"));
  }

  @Test
  void hasTrustBalanceKeepsOnlyClientsHoldingTrustMoney() throws Exception {
    assertThat(ids(list(owner(), "?hasTrustBalance=true")))
        .contains(clientA.toString(), clientD.toString())
        .doesNotContain(clientB.toString(), clientC.toString(), bulkClient.toString());
  }

  @Test
  void hasTrustBalanceWithoutTrustVisibilityReturnsNoRows() throws Exception {
    // no VIEW_TRUST: filtering by the redacted column would leak it
    assertThat(ids(list(TestJwtFactory.memberJwt(ORG_ID, BARE), "?hasTrustBalance=true")))
        .isEmpty();
    // the same caller without the param still gets the list
    assertThat(ids(list(TestJwtFactory.memberJwt(ORG_ID, BARE), ""))).contains(clientA.toString());
  }

  @Test
  void hasTrustBalanceWithTrustModuleOffReturnsNoRows() throws Exception {
    setTrustModule(false);
    try {
      assertThat(ids(list(owner(), "?hasTrustBalance=true"))).isEmpty();
      assertThat(row(list(owner(), ""), clientA)).containsEntry("trustBalance", null);
    } finally {
      setTrustModule(true);
    }
  }

  @Test
  void showIncompleteKeepsOnlyClientsBelowOneHundredPercent() throws Exception {
    var seeded = List.of(clientA, clientB, clientC, clientD);
    var incomplete = new ArrayList<String>();
    var notIncomplete = new ArrayList<String>();
    for (UUID id : seeded) {
      var score = completenessSummary(id);
      boolean below =
          ((Number) score.get("totalRequired")).intValue() > 0
              && ((Number) score.get("percentage")).intValue() < 100;
      (below ? incomplete : notIncomplete).add(id.toString());
    }
    assertThat(completenessSummary(clientD)).containsEntry("percentage", 100);
    assertThat(incomplete).as("fixture has incomplete clients").contains(clientA.toString());
    assertThat(notIncomplete).contains(clientD.toString());

    var json = list(owner(), "?showIncomplete=true");
    assertThat(ids(json)).containsAll(incomplete).doesNotContainAnyElementsOf(notIncomplete);
    assertThat(row(json, clientA)).containsEntry("completenessPercent", completenessOf(clientA));
    assertThat(row(list(owner(), ""), clientD)).containsEntry("completenessPercent", 100);
  }

  @Test
  void lifecycleStatusStillFiltersAndCombinesWithTheNewParams() throws Exception {
    assertThat(ids(list(owner(), "?lifecycleStatus=PROSPECT")))
        .contains(clientC.toString())
        .doesNotContain(clientA.toString(), clientB.toString(), clientD.toString());
    assertThat(ids(list(owner(), "?lifecycleStatus=ACTIVE&ficaStatus=DONE")))
        .contains(clientA.toString())
        .doesNotContain(
            clientB.toString(), clientC.toString(), clientD.toString(), bulkClient.toString());
    assertThat(ids(list(owner(), "?lifecycleStatus=ACTIVE&hasTrustBalance=true")))
        .contains(clientA.toString())
        .doesNotContain(clientD.toString(), clientB.toString());
  }

  @Test
  void savedViewNarrowsFirstThenTheNewParamsApply() throws Exception {
    String viewId = createSavedView("CLR View", Map.of("search", "CLR"));

    assertThat(ids(list(owner(), "?view=" + viewId)))
        .contains(clientA.toString(), clientB.toString(), clientC.toString(), clientD.toString())
        .doesNotContain(bulkClient.toString());
    assertThat(ids(list(owner(), "?view=" + viewId + "&ficaStatus=DONE")))
        .contains(clientA.toString(), clientD.toString())
        .doesNotContain(clientB.toString(), clientC.toString(), bulkClient.toString());
    assertThat(ids(list(owner(), "?view=" + viewId + "&hasTrustBalance=true&showIncomplete=true")))
        .contains(clientA.toString())
        .doesNotContain(
            clientB.toString(), clientC.toString(), clientD.toString(), bulkClient.toString());
  }

  // --- Statement cap ---

  @Test
  void fiftyClientPageCostsAtMostTenStatementsAndMatchesAOneClientPage() throws Exception {
    Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    assertThat(stats.isStatisticsEnabled())
        .as("hibernate.generate_statistics must be on in application-test.yml")
        .isTrue();

    // Default path: no view, no params.
    var page = listCounted(stats, new ClientListQuery(null, null, null, false, false, Map.of()));
    assertThat(page.rows()).hasSizeGreaterThanOrEqualTo(CLIENT_PAGE);
    // the page really exercised every aggregate (tags, trust, completed FICA, fee notes, matters)
    var a = page.rows().stream().filter(r -> r.id().equals(clientA)).findFirst().orElseThrow();
    assertThat(a.tags()).isNotEmpty();
    assertThat(a.trustBalance()).isEqualByComparingTo("15000.00");
    assertThat(a.ficaStatus()).isEqualTo("DONE");
    assertThat(a.outstandingFeeNoteTotal()).isEqualByComparingTo("1500.00");
    assertThat(a.openMatterCount()).isEqualTo(2);
    // ...and the checklist branch of the FICA rule (E: DONE from its onboarding checklist)
    var e = page.rows().stream().filter(r -> r.id().equals(clientE)).findFirst().orElseThrow();
    assertThat(e.ficaStatus()).isEqualTo("DONE");
    assertThat(page.statements())
        .as("GET /api/clients statement cap (Epic 615B, D7)")
        .isLessThanOrEqualTo(STATEMENT_CAP);

    // One-client page (D is the only DORMANT client; tag + completed FICA + ledger card) → same
    // statement count → no N+1.
    var single =
        listCounted(
            stats,
            new ClientListQuery(null, LifecycleStatus.DORMANT, null, false, false, Map.of()));
    assertThat(single.rows()).extracting(ClientListRow::id).containsExactly(clientD);
    var d = single.rows().getFirst();
    assertThat(d.tags()).isNotEmpty();
    assertThat(d.ficaStatus()).isEqualTo("DONE");
    assertThat(d.trustBalance()).isEqualByComparingTo("200.00");
    assertThat(page.statements())
        .as("statement count is independent of page size")
        .isEqualTo(single.statements());
  }

  // --- helpers ---

  private record CountedPage(List<ClientListRow> rows, long statements) {}

  /** Runs the list as the owner (all capabilities) and counts the JDBC statements it prepares. */
  private CountedPage listCounted(Statistics stats, ClientListQuery query) throws Exception {
    var actor = new ActorContext(ownerId, "owner");
    // warm-up (plan caches etc.) — not counted
    TenantTestSupport.callAsActor(
        schema,
        ORG_ID,
        ownerId,
        "owner",
        Capability.ALL_NAMES,
        () -> customerService.listClientRows(query, actor));
    long[] statements = new long[1];
    List<ClientListRow> rows =
        TenantTestSupport.callAsActor(
            schema,
            ORG_ID,
            ownerId,
            "owner",
            Capability.ALL_NAMES,
            () -> {
              stats.clear();
              var result = customerService.listClientRows(query, actor);
              statements[0] = stats.getPrepareStatementCount();
              return result;
            });
    System.out.printf(
        "%n[ClientListRowIntegrationTest] rows=%d prepared statements=%d%n",
        rows.size(), statements[0]);
    return new CountedPage(rows, statements[0]);
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private String list(RequestPostProcessor jwt, String query) throws Exception {
    return mockMvc
        .perform(get("/api/clients" + query).with(jwt))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static Map<String, Object> row(String json, UUID id) {
    List<Map<String, Object>> rows = JsonPath.read(json, "$[?(@.id == '" + id + "')]");
    assertThat(rows).as("row %s present", id).hasSize(1);
    return rows.getFirst();
  }

  private static List<String> ids(String json) {
    return JsonPath.read(json, "$[*].id");
  }

  private static BigDecimal money(Map<String, Object> row, String field) {
    Object value = row.get(field);
    assertThat(value).as("%s present", field).isNotNull();
    return new BigDecimal(value.toString());
  }

  private Map<String, Object> completenessSummary(UUID clientId) throws Exception {
    String json =
        mockMvc
            .perform(
                get("/api/clients/completeness-summary")
                    .with(owner())
                    .param("clientIds", clientId.toString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$['" + clientId + "']");
  }

  private Object completenessOf(UUID clientId) throws Exception {
    return completenessSummary(clientId).get("percentage");
  }

  private UUID saveClient(Customer customer) {
    return transactionTemplate.execute(tx -> customerRepository.saveAndFlush(customer).getId());
  }

  private void saveMatter(String name, UUID clientId, boolean archived) {
    var matter = new Project(name, "d", ownerId);
    matter.setCustomerId(clientId);
    if (archived) {
      matter.archive(ownerId);
    }
    projectRepository.saveAndFlush(matter);
  }

  private Invoice newInvoice(UUID clientId, long amount, String invoiceCurrency, LocalDate due) {
    var invoice =
        new Invoice(
            clientId, invoiceCurrency, "CLR Client", "clr@test.com", null, "CLR Org", ownerId);
    invoice.updateDraft(due, null, null, BigDecimal.ZERO);
    invoice.recalculateTotals(BigDecimal.valueOf(amount), false, BigDecimal.ZERO, false);
    return invoice;
  }

  private UUID seedSentInvoice(UUID clientId, long amount, String invoiceCurrency, LocalDate due) {
    var invoice = newInvoice(clientId, amount, invoiceCurrency, due);
    invoice.approve("CLR-" + (++invoiceSeq), ownerId, BillingSettings.DEFAULT_PAYMENT_TERMS_DAYS);
    invoice.markSent();
    return invoiceRepository.save(invoice).getId();
  }

  private void setTrustModule(boolean enabled) {
    asOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(enabled ? List.of("trust_accounting") : List.of());
                  orgSettingsRepository.save(settings);
                }));
  }

  private String createSavedView(String name, Map<String, Object> filters) {
    var idHolder = new String[1];
    asOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    idHolder[0] =
                        savedViewRepository
                            .saveAndFlush(
                                // ADR-342: storage key, do not rename
                                new SavedView("CUSTOMER", name, filters, null, false, ownerId, 0))
                            .getId()
                            .toString()));
    return idHolder[0];
  }

  private UUID sync(String subject, String name, String role) throws Exception {
    return UUID.fromString(
        TestMemberHelper.syncMember(mockMvc, ORG_ID, subject, subject + "@test.com", name, role));
  }

  private void asOwner(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String createCustomRole(String name, String capability) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(UUID memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  // FICA fixtures (domain-specific; mirror FicaOutstandingIntegrationTest)

  private String createPortalContact(UUID clientId, String email) {
    String contactId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO \"%s\".portal_contacts (id, org_id, customer_id, email, display_name, role, status, created_at, updated_at) VALUES (?::uuid, ?, ?::uuid, ?, ?, 'PRIMARY', 'ACTIVE', now(), now())"
            .formatted(schema),
        contactId,
        ORG_ID,
        clientId.toString(),
        email,
        "CLR Contact");
    return contactId;
  }

  private String createFicaTemplate() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/request-templates")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "FICA Onboarding Pack",
                          "description": "FICA onboarding",
                          "items": [
                            {"name": "ID copy", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 0},
                            {"name": "Proof of residence", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 1}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String templateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    // Back-fill the pack_id column — the public API does not expose it.
    jdbcTemplate.update(
        "UPDATE \"%s\".request_templates SET pack_id = ? WHERE id = ?::uuid".formatted(schema),
        PACK_ID,
        templateId);
    return templateId;
  }

  private String createFicaRequest(UUID clientId, String contactId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "requestTemplateId": "%s",
                          "clientId": "%s",
                          "portalContactId": "%s",
                          "reminderIntervalDays": 7
                        }
                        """
                            .formatted(ficaTemplateId, clientId, contactId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  /** SENT → items SUBMITTED (JDBC, the portal's job) → accept each via the API → COMPLETED. */
  private void completeWithAllItemsAccepted(String requestId) throws Exception {
    mockMvc
        .perform(post("/api/information-requests/{id}/send", requestId).with(owner()))
        .andExpect(status().isOk());
    var detail =
        mockMvc
            .perform(get("/api/information-requests/{id}", requestId).with(owner()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> itemIds = JsonPath.read(detail, "$.items[*].id");
    for (String itemId : itemIds) {
      jdbcTemplate.update(
          "UPDATE \"%s\".request_items SET status = 'SUBMITTED', submitted_at = now(), document_id = ?::uuid WHERE id = ?::uuid"
              .formatted(schema),
          UUID.randomUUID().toString(),
          itemId);
    }
    for (String itemId : itemIds) {
      mockMvc
          .perform(
              post("/api/information-requests/{id}/items/{itemId}/accept", requestId, itemId)
                  .with(owner()))
          .andExpect(status().isOk());
    }
  }
}
