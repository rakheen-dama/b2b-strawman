package io.b2mash.b2b.b2bstrawman.compliance;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles.csv;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles.mapping;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles.row;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.attest;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.detail;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validatedClients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnCommitService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDrainJobHandler;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnStatements;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.Validated;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestDrain;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 93 655B (task 655.15, §17.3.4–§17.3.5, ADR-388): the FICA projection reads the 655A
 * attestations, and the review queue is the client list filtered to {@code ficaStatus=REVIEW}.
 * Fixture clients come from real clients take-ons (upload → map → validate → attest → commit via
 * {@link TakeOnTestDrain}); the Kazi clients are created and onboarded through the API. Own org;
 * every list assertion is scoped to this class's ids.
 *
 * <p>Fixture dates sit years away from today so "review date passed" never flips during the phase:
 * LOW 36 / MEDIUM 24 / HIGH 12 months (the defaults, FD-7) from the CDD date.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FicaReviewQueueIntegrationTest {

  private static final String ORG_ID = "org_fica_review_queue_test";
  private static final String OWNER = "user_frq_owner";
  private static final String NO_CLIENTS = "user_frq_no_cm";
  private static final String MEMBER = "user_frq_member";
  private static final String ADMIN = "user_frq_admin";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;

  private String schema;
  private UUID ownerId;
  private String onboardingTemplateId;
  private UUID batchId;

  // Imported (take-on) clients
  private UUID clean; // LOW 2026-02-01 → 2029-02-01, nothing outstanding → ATTESTED
  private UUID unrated; // no rating → UNRATED, no review date
  private UUID boOutstanding; // MEDIUM 2026-06-01 → 2028-06-01, BO outstanding
  private UUID mediumLater; // MEDIUM 2026-09-01 → 2028-09-01, BO outstanding
  private UUID notScreened; // LOW 2026-02-01 → 2029-02-01, not screened
  private UUID reviewPassed; // HIGH 2020-02-01 → 2021-02-01 (passed)
  private UUID sofOutstanding; // LOW 2026-02-01 → 2029-02-01, source of funds via batch default
  private UUID checklistLater; // UNRATED; a Kazi onboarding checklist runs on it (FD-29)

  // Kazi clients
  private UUID uiProspect; // created in Kazi, never onboarded → NOT_STARTED
  private UUID uiOnboarding; // created in Kazi, onboarding started → IN_PROGRESS

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "FICA Review Queue Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "frq_owner@test.com", "Queue Owner", "owner"));

    var batch =
        validatedClients(
            mockMvc,
            ownerJwt(),
            csv(
                row("Clean Client", "clean@example.test")
                    .cddDate("01/02/2026")
                    .records("Box Q-CLEAN")
                    .ref("Q-CLEAN"),
                row("Unrated Client", "unrated@example.test")
                    .rating("")
                    .records("Box Q-UNRATED")
                    .ref("Q-UNRATED"),
                row("Owner Unknown", "bo@example.test")
                    .rating("Medium")
                    .cddDate("01/06/2026")
                    .bo("Outstanding")
                    .records("Box Q-BO")
                    .ref("Q-BO"),
                row("Medium Later", "medium@example.test")
                    .rating("Medium")
                    .cddDate("01/09/2026")
                    .bo("Outstanding")
                    .records("Box Q-MED")
                    .ref("Q-MED"),
                row("Never Screened", "ns@example.test")
                    .cddDate("01/02/2026")
                    .screening("Not screened", "")
                    .records("Box Q-NS")
                    .ref("Q-NS"),
                row("Overdue Review", "overdue@example.test")
                    .rating("High")
                    .cddDate("01/02/2020")
                    .records("Box Q-PASSED")
                    .ref("Q-PASSED"),
                row("Checklist Later", "checklist@example.test")
                    .rating("")
                    .records("Box Q-CHECK")
                    .ref("Q-CHECK")),
            mapping());
    attestAndCommit(batch);
    batchId = batch.batchId();
    clean = clientIdByRef(batchId, "Q-CLEAN");
    unrated = clientIdByRef(batchId, "Q-UNRATED");
    boOutstanding = clientIdByRef(batchId, "Q-BO");
    mediumLater = clientIdByRef(batchId, "Q-MED");
    notScreened = clientIdByRef(batchId, "Q-NS");
    reviewPassed = clientIdByRef(batchId, "Q-PASSED");
    checklistLater = clientIdByRef(batchId, "Q-CHECK");

    // Source of funds outstanding through the batch default (the file has no such column).
    var sof =
        validatedClients(
            mockMvc,
            ownerJwt(),
            csv(
                row("Funds Unknown", "sof@example.test")
                    .cddDate("01/02/2026")
                    .records("Box Q-SOF")
                    .ref("Q-SOF")),
            mapping(Map.of("sourceOfFundsStatus", "OUTSTANDING")));
    attestAndCommit(sof);
    sofOutstanding = clientIdByRef(sof.batchId(), "Q-SOF");

    // Kazi clients: an onboarding template (auto-instantiated on Start Onboarding).
    onboardingTemplateId =
        TestChecklistHelper.createOnboardingTemplate(
            mockMvc, ownerJwt(), "FICA Review Queue Onboarding");
    uiProspect =
        UUID.fromString(
            TestEntityHelper.createCustomer(
                mockMvc, ownerJwt(), "UI Prospect", "ui.prospect@example.test"));
    uiOnboarding =
        UUID.fromString(
            TestEntityHelper.createCustomer(
                mockMvc, ownerJwt(), "UI Onboarding", "ui.onboarding@example.test"));
    TestChecklistHelper.startOnboarding(mockMvc, uiOnboarding.toString(), ownerJwt());

    // A member whose custom role lacks CUSTOMER_MANAGEMENT.
    String noClientsId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, NO_CLIENTS, "frq_nocm@test.com", "No Clients", "member");
    assignRole(noClientsId, createRole("FRQ invoicing only", "INVOICING"));
    // The seeded system roles: Member has no CUSTOMER_MANAGEMENT, Admin does (D-148).
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "frq_member@test.com", "Plain Member", "member");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, ADMIN, "frq_admin@test.com", "Queue Admin", "admin");
  }

  // --- the review queue (ficaStatus=REVIEW) ---

  @Test
  void reviewQueueListsAttestedReviewOnlyNextReviewDateAscendingUnratedFirst() throws Exception {
    // checklistLater is left out: the FD-29 case moves it out of the queue (order-independent).
    var own =
        Set.of(
            clean,
            unrated,
            boOutstanding,
            mediumLater,
            notScreened,
            reviewPassed,
            sofOutstanding,
            uiProspect,
            uiOnboarding);
    var rows = listRows("REVIEW", own);

    assertThat(rows.stream().map(r -> UUID.fromString((String) r.get("id"))).toList())
        .containsExactly(
            unrated, // no review date (UNRATED) first
            reviewPassed, // 2021-02-01
            boOutstanding, // 2028-06-01
            mediumLater, // 2028-09-01
            sofOutstanding, // 2029-02-01, "Funds Unknown" …
            notScreened); // … before "Never Screened" (name breaks the date tie)
    assertThat(rows).allSatisfy(r -> assertThat(r.get("ficaStatus")).isEqualTo("ATTESTED_REVIEW"));

    var byId = rows.stream().collect(Collectors.toMap(r -> (String) r.get("id"), r -> r));
    assertRow(byId.get(unrated.toString()), List.of("UNRATED"), "UNRATED", null, "Box Q-UNRATED");
    assertRow(
        byId.get(reviewPassed.toString()),
        List.of("REVIEW_DATE_PASSED"),
        "HIGH",
        "2021-02-01",
        "Box Q-PASSED");
    assertRow(
        byId.get(boOutstanding.toString()),
        List.of("BENEFICIAL_OWNERSHIP_OUTSTANDING"),
        "MEDIUM",
        "2028-06-01",
        "Box Q-BO");
    assertRow(
        byId.get(sofOutstanding.toString()),
        List.of("SOURCE_OF_FUNDS_OUTSTANDING"),
        "LOW",
        "2029-02-01",
        "Box Q-SOF");
    assertRow(
        byId.get(notScreened.toString()), List.of("NOT_SCREENED"), "LOW", "2029-02-01", "Box Q-NS");
  }

  @Test
  void outstandingIncludesAttestedReviewAndKaziClientsButNotAttested() throws Exception {
    var ids = ids(listRows("OUTSTANDING", null));
    assertThat(ids)
        .contains(
            unrated,
            boOutstanding,
            mediumLater,
            notScreened,
            reviewPassed,
            sofOutstanding,
            uiProspect,
            uiOnboarding)
        .doesNotContain(clean);
  }

  @Test
  void doneIncludesAttestedButNotReviewDueOrKaziOutstanding() throws Exception {
    var rows = listRows("DONE", null);
    assertThat(ids(rows))
        .contains(clean)
        .doesNotContain(
            unrated,
            boOutstanding,
            mediumLater,
            notScreened,
            reviewPassed,
            sofOutstanding,
            uiProspect,
            uiOnboarding);
    var cleanRow =
        rows.stream().filter(r -> clean.toString().equals(r.get("id"))).findFirst().orElseThrow();
    assertRow(cleanRow, List.of(), "LOW", "2029-02-01", "Box Q-CLEAN");
    assertThat(cleanRow.get("ficaStatus")).isEqualTo("ATTESTED");
    assertThat(cleanRow.get("ficaLastVerifiedAt")).isNull();
  }

  // --- attestation detail needs CUSTOMER_MANAGEMENT on the ungated reads (D-148, F-655B-1) ---

  @Test
  void withoutClientManagementTheListKeepsStatusAndOrderButBlanksAttestationDetail()
      throws Exception {
    var own =
        Set.of(
            unrated, boOutstanding, mediumLater, notScreened, reviewPassed, sofOutstanding, clean);
    var ownerOrder = ids(listRows("REVIEW", own, ownerJwt()));
    for (var caller :
        List.of(
            TestJwtFactory.memberJwt(ORG_ID, MEMBER),
            TestJwtFactory.customRoleJwt(ORG_ID, NO_CLIENTS))) {
      var review = listRows("REVIEW", own, caller);
      // the review queue (filter + server-side order) is the same as the owner's …
      assertThat(ids(review)).isNotEmpty().isEqualTo(ownerOrder);
      assertThat(review)
          .allSatisfy(r -> assertThat(r.get("ficaStatus")).isEqualTo("ATTESTED_REVIEW"))
          // … but every row's attestation detail is blank
          .allSatisfy(r -> assertRow(r, List.of(), null, null, null));

      var done = listRows("DONE", Set.of(clean), caller);
      assertThat(done).hasSize(1);
      assertThat(done.getFirst().get("ficaStatus")).isEqualTo("ATTESTED");
      assertRow(done.getFirst(), List.of(), null, null, null);
    }
    // the admin holds CUSTOMER_MANAGEMENT through the system role and sees the detail
    var adminRow =
        listRows("REVIEW", Set.of(boOutstanding), TestJwtFactory.adminJwt(ORG_ID, ADMIN));
    assertRow(
        adminRow.getFirst(),
        List.of("BENEFICIAL_OWNERSHIP_OUTSTANDING"),
        "MEDIUM",
        "2028-06-01",
        "Box Q-BO");
  }

  @Test
  void withoutClientManagementFicaStatusKeepsStatusButBlanksReasonsAndAttestationId()
      throws Exception {
    for (var caller :
        List.of(
            TestJwtFactory.memberJwt(ORG_ID, MEMBER),
            TestJwtFactory.customRoleJwt(ORG_ID, NO_CLIENTS))) {
      mockMvc
          .perform(get("/api/clients/{id}/fica-status", reviewPassed).with(caller))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.clientId").value(reviewPassed.toString()))
          .andExpect(jsonPath("$.status").value("ATTESTED_REVIEW"))
          .andExpect(jsonPath("$.reviewReasons").isArray())
          .andExpect(jsonPath("$.reviewReasons").isEmpty())
          .andExpect(jsonPath("$.attestationId").doesNotExist());
      mockMvc
          .perform(get("/api/clients/{id}/fica-status", clean).with(caller))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ATTESTED"))
          .andExpect(jsonPath("$.attestationId").doesNotExist());
    }
    // owner and admin (CUSTOMER_MANAGEMENT) see the detail
    for (var caller : List.of(ownerJwt(), TestJwtFactory.adminJwt(ORG_ID, ADMIN))) {
      mockMvc
          .perform(get("/api/clients/{id}/fica-status", reviewPassed).with(caller))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ATTESTED_REVIEW"))
          .andExpect(jsonPath("$.reviewReasons[0]").value("REVIEW_DATE_PASSED"))
          .andExpect(jsonPath("$.attestationId").value(attestationIdOf(reviewPassed).toString()));
    }
  }

  // --- the single read, the dashboard widget, FD-29 ---

  @Test
  void ficaStatusCarriesAttestedStatusReasonsAndAttestationId() throws Exception {
    mockMvc
        .perform(get("/api/clients/{id}/fica-status", clean).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ATTESTED"))
        .andExpect(jsonPath("$.attestationId").value(attestationIdOf(clean).toString()))
        .andExpect(jsonPath("$.reviewReasons").isEmpty())
        .andExpect(jsonPath("$.lastVerifiedAt").doesNotExist())
        .andExpect(jsonPath("$.requestId").doesNotExist())
        .andExpect(jsonPath("$.checklistInstanceId").doesNotExist());
    mockMvc
        .perform(get("/api/clients/{id}/fica-status", unrated).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ATTESTED_REVIEW"))
        .andExpect(jsonPath("$.reviewReasons.length()").value(1))
        .andExpect(jsonPath("$.reviewReasons[0]").value("UNRATED"))
        .andExpect(jsonPath("$.attestationId").value(attestationIdOf(unrated).toString()));
    // A Kazi client's status is unchanged by the slice (R-10).
    mockMvc
        .perform(get("/api/clients/{id}/fica-status", uiOnboarding).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.checklistInstanceId").exists())
        .andExpect(jsonPath("$.attestationId").doesNotExist())
        .andExpect(jsonPath("$.reviewReasons").isEmpty());
    mockMvc
        .perform(get("/api/clients/{id}/fica-status", uiProspect).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NOT_STARTED"));
  }

  @Test
  void dashboardWidgetListsAttestedReviewButNotAttested() throws Exception {
    String json =
        mockMvc
            .perform(get("/api/dashboard/fica-outstanding?limit=50").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Map<String, Object>> clients = JsonPath.read(json, "$.clients");
    Map<String, Object> statusById =
        clients.stream()
            .collect(Collectors.toMap(c -> (String) c.get("clientId"), c -> c.get("ficaStatus")));

    for (UUID review :
        List.of(unrated, boOutstanding, mediumLater, notScreened, reviewPassed, sofOutstanding)) {
      assertThat(statusById)
          .as("client %s", review)
          .containsEntry(review.toString(), "ATTESTED_REVIEW");
    }
    assertThat(statusById).containsEntry(uiOnboarding.toString(), "IN_PROGRESS");
    assertThat(statusById).doesNotContainKey(clean.toString());
  }

  @Test
  void aKaziOnboardingChecklistSupersedesTheAttestation() throws Exception {
    // FD-29: Kazi evidence wins — the client leaves the review queue once a checklist runs.
    mockMvc
        .perform(get("/api/clients/{id}/fica-status", checklistLater).with(ownerJwt()))
        .andExpect(jsonPath("$.status").value("ATTESTED_REVIEW"));

    mockMvc
        .perform(
            post("/api/clients/{id}/checklists", checklistLater)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\":\"%s\"}".formatted(onboardingTemplateId)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/api/clients/{id}/fica-status", checklistLater).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.checklistInstanceId").exists())
        .andExpect(jsonPath("$.attestationId").doesNotExist())
        .andExpect(jsonPath("$.reviewReasons").isEmpty());
    assertThat(ids(listRows("REVIEW", null))).doesNotContain(checklistLater);
    assertThat(ids(listRows("OUTSTANDING", null))).contains(checklistLater);

    TestChecklistHelper.completeChecklistItems(mockMvc, checklistLater.toString(), ownerJwt());
    mockMvc
        .perform(get("/api/clients/{id}/fica-status", checklistLater).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.lastVerifiedAt").exists());
    assertThat(ids(listRows("DONE", null))).contains(checklistLater);
  }

  // --- GET /api/clients/{clientId}/fica-attestation ---

  @Test
  void ficaAttestationReturnsTheActiveAttestationWithWhoAttested() throws Exception {
    String roleName =
        jdbc.queryForObject(
            "SELECT role_name FROM \"%s\".take_on_sign_offs WHERE batch_id = ?".formatted(schema),
            String.class,
            batchId);
    mockMvc
        .perform(get("/api/clients/{id}/fica-attestation", boOutstanding).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(attestationIdOf(boOutstanding).toString()))
        .andExpect(jsonPath("$.clientId").value(boOutstanding.toString()))
        .andExpect(jsonPath("$.customerId").doesNotExist())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.takeOnBatchId").value(batchId.toString()))
        .andExpect(jsonPath("$.cddCompletedDate").value("2026-06-01"))
        .andExpect(jsonPath("$.lastReviewDate").doesNotExist())
        .andExpect(jsonPath("$.riskRating").value("MEDIUM"))
        .andExpect(jsonPath("$.ficaClientType").value("NATURAL_PERSON"))
        .andExpect(jsonPath("$.beneficialOwnershipStatus").value("OUTSTANDING"))
        .andExpect(jsonPath("$.screeningStatus").value("SCREENED_CLEAR"))
        .andExpect(jsonPath("$.screeningDate").value("2024-02-01"))
        .andExpect(jsonPath("$.sourceOfFundsStatus").value("NOT_REQUIRED"))
        .andExpect(jsonPath("$.cddRecordsLocation").value("Box Q-BO"))
        .andExpect(jsonPath("$.sourceSystemRef").value("Q-BO"))
        .andExpect(jsonPath("$.nextReviewDate").value("2028-06-01"))
        .andExpect(jsonPath("$.attestedBy").value(ownerId.toString()))
        .andExpect(jsonPath("$.attestedByName").value("Queue Owner"))
        .andExpect(jsonPath("$.attestedByRole").value(roleName))
        .andExpect(jsonPath("$.statementVersion").value(TakeOnStatements.FICA_ATTEST_V1))
        .andExpect(jsonPath("$.attestedAt").exists());
    mockMvc
        .perform(get("/api/clients/{id}/fica-attestation", unrated).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.riskRating").value("UNRATED"))
        .andExpect(jsonPath("$.nextReviewDate").doesNotExist());
  }

  @Test
  void ficaAttestationIs404WithoutOneAnd403WithoutClientManagement() throws Exception {
    mockMvc
        .perform(get("/api/clients/{id}/fica-attestation", uiProspect).with(ownerJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
    mockMvc
        .perform(get("/api/clients/{id}/fica-attestation", UUID.randomUUID()).with(ownerJwt()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/clients/{id}/fica-attestation", clean)
                .with(TestJwtFactory.customRoleJwt(ORG_ID, NO_CLIENTS)))
        .andExpect(status().isForbidden());
  }

  // --- review intervals apply to attestations created afterwards (655.14) ---

  @Test
  void changedReviewIntervalAppliesToLaterTakeOnsOnly() throws Exception {
    patchCompliance("{\"ficaReviewMonthsLow\":60}");
    try {
      var later =
          validatedClients(
              mockMvc,
              ownerJwt(),
              csv(
                  row("Later Low", "later.low@example.test")
                      .cddDate("01/02/2026")
                      .records("Box Q-LATER")
                      .ref("Q-LATER")),
              mapping());
      attestAndCommit(later);
      UUID laterLow = clientIdByRef(later.batchId(), "Q-LATER");

      mockMvc
          .perform(get("/api/clients/{id}/fica-attestation", laterLow).with(ownerJwt()))
          .andExpect(jsonPath("$.nextReviewDate").value("2031-02-01")); // 60 months
      mockMvc
          .perform(get("/api/clients/{id}/fica-attestation", clean).with(ownerJwt()))
          .andExpect(jsonPath("$.nextReviewDate").value("2029-02-01")); // stored, unchanged
    } finally {
      patchCompliance("{\"ficaReviewMonthsLow\":36}");
    }
  }

  // --- helpers ---

  /** {@code GET /api/clients?ficaStatus=…}, in response order, kept to {@code only} when given. */
  private List<Map<String, Object>> listRows(String ficaStatus, Set<UUID> only) throws Exception {
    return listRows(ficaStatus, only, ownerJwt());
  }

  /** As {@link #listRows(String, Set)}, as {@code caller}. */
  private List<Map<String, Object>> listRows(
      String ficaStatus, Set<UUID> only, JwtRequestPostProcessor caller) throws Exception {
    String json =
        mockMvc
            .perform(get("/api/clients?ficaStatus=" + ficaStatus).with(caller))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Map<String, Object>> rows = JsonPath.read(json, "$[*]");
    return only == null
        ? rows
        : rows.stream().filter(r -> only.contains(UUID.fromString((String) r.get("id")))).toList();
  }

  private static List<UUID> ids(List<Map<String, Object>> rows) {
    return rows.stream().map(r -> UUID.fromString((String) r.get("id"))).toList();
  }

  private static void assertRow(
      Map<String, Object> row,
      List<String> reasons,
      String rating,
      String nextReviewDate,
      String recordsLocation) {
    assertThat(row.get("ficaReviewReasons")).isEqualTo(reasons);
    assertThat(row.get("ficaRiskRating")).isEqualTo(rating);
    assertThat(row.get("ficaNextReviewDate")).isEqualTo(nextReviewDate);
    assertThat(row.get("ficaRecordsLocation")).isEqualTo(recordsLocation);
  }

  private void patchCompliance(String body) throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/compliance")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  private void attestAndCommit(Validated batch) throws Exception {
    attest(mockMvc, ownerJwt(), batch.batchId(), batch.hash(), mappingDefaults(batch.batchId()))
        .andExpect(status().isOk());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, batch.batchId(), batch.hash());
  }

  private Map<String, String> mappingDefaults(UUID batchId) throws Exception {
    Map<String, String> defaults =
        JsonPath.read(
            detail(mockMvc, ownerJwt(), batchId).andReturn().getResponse().getContentAsString(),
            "$.sources[0].mapping.defaults");
    return defaults == null ? Map.of() : defaults;
  }

  /** Read-only: the client the take-on row created. */
  private UUID clientIdByRef(UUID batchId, String ref) {
    return jdbc.queryForObject(
        "SELECT result_entity_id FROM \"%s\".take_on_rows WHERE batch_id = ? AND source_ref = ?"
            .formatted(schema),
        UUID.class,
        batchId,
        ref);
  }

  /** Read-only: the client's active attestation id. */
  private UUID attestationIdOf(UUID clientId) {
    return jdbc.queryForObject(
        "SELECT id FROM \"%s\".client_fica_attestations WHERE customer_id = ? AND status = 'ACTIVE'"
            .formatted(schema),
        UUID.class,
        clientId);
  }

  private String createRole(String name, String... capabilities) throws Exception {
    String caps =
        Arrays.stream(capabilities)
            .map(c -> "\"" + c + "\"")
            .collect(Collectors.joining(",", "[", "]"));
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"%s\",\"description\":\"Test role\",\"capabilities\":%s}"
                            .formatted(name, caps)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(String memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
