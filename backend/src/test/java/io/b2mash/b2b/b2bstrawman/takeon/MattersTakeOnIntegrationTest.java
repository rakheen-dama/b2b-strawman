package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnMattersFiles.csv;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnMattersFiles.mapping;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnMattersFiles.row;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.detail;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rowContent;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rows;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validatedMatters;
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
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalProjectView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectPriority;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.Validated;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.MattersDatasetHandler;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 93 656A (task 656.5, §17.3.6): the matters take-on through the endpoints the wizard uses —
 * upload → map → validate → commit ({@link TakeOnTestDrain}: the claim, then the drain as the job
 * worker runs it; no sign-off) → results. A matter resolves its client by email, then by the
 * previous-system ID of a client taken on by an earlier clients batch, then by ID number; orphans
 * and clients whose lifecycle refuses a matter are errors; duplicates are skipped; the committed
 * matter is an ordinary matter (lead, client link, portal row, {@code project.created}) with no
 * template, and the responsible attorney becomes its lead. Every assertion is scoped to this test's
 * own rows (id-scope). No go-live item is acknowledged in this tenant: the checklist gates nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MattersTakeOnIntegrationTest {

  private static final String ORG_ID = "org_matters_takeon_test";
  private static final String OWNER = "user_mto_owner";
  private static final String ADMIN = "user_mto_admin";
  private static final String LEAVER = "user_mto_leaver";
  private static final String OWNER_EMAIL = "mto_owner@test.com";
  private static final String ADMIN_EMAIL = "mto_admin@test.com";
  private static final String LEAVER_EMAIL = "mto_leaver@test.com";
  private static final String THANDI_EMAIL = "mt.thandi@example.test";
  private static final String TAKEN_ON_REF = "MT-C-100";
  private static final String ID_NUMBER = "7701015009081";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private CustomerRepository customers;
  @Autowired private ProjectRepository projects;
  @Autowired private PortalReadModelRepository portalReadModel;

  private String schema;
  private UUID ownerId;
  private UUID adminId;
  private UUID thandi; // an existing ACTIVE client (email)
  private UUID takenOn; // a client taken on by an earlier clients batch (previous-system ID)
  private UUID byIdNumber; // an existing ACTIVE client (ID number)

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Matters Take-on Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, OWNER_EMAIL, "Matters Owner", "owner"));
    adminId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, ADMIN, ADMIN_EMAIL, "Matters Admin", "admin"));
    thandi =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Thandi Nkosi", THANDI_EMAIL, ownerId))
                    .getId());
    byIdNumber = activeClientWithIdNumber("Sipho Dube", "mt.sipho@example.test", ID_NUMBER);

    // An earlier clients batch, committed: its client is found by its previous-system ID.
    var clients =
        TakeOnTestApi.validatedClients(
            mockMvc,
            owner(),
            TakeOnClientsFiles.csv(
                TakeOnClientsFiles.row("Lerato Molefe", "mt.lerato@example.test")
                    .ref(TAKEN_ON_REF)),
            TakeOnClientsFiles.mapping());
    TakeOnTestApi.attest(mockMvc, owner(), clients.batchId(), clients.hash(), Map.of())
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
  void theClient_resolvesByEmailThenPreviousSystemIdOfAnEarlierBatchThenIdNumber()
      throws Exception {
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("Estate late M Nkosi").clientEmail("MT.Thandi@example.test").ref("R1-1"),
                row("Transfer erf 12").clientRef("mt-c-100").ref("R1-2"),
                row("Dube v Dube").clientIdNumber(ID_NUMBER).ref("R1-3"),
                row("Cascade matter")
                    .clientEmail("nobody@example.test")
                    .clientRef(TAKEN_ON_REF)
                    .ref("R1-4"),
                row("Email first").clientEmail(THANDI_EMAIL).clientRef(TAKEN_ON_REF).ref("R1-5")),
            mapping());

    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(5);
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.error")).isZero();
    // no sign-off for matters: COMMIT is offered straight away, never ATTEST
    assertThat((List<String>) JsonPath.read(batch.json(), "$.allowedActions"))
        .contains("COMMIT")
        .doesNotContain("ATTEST");
    var byRef = rowsByRef(batch.batchId());
    assertResolved(byRef.get("R1-1"), thandi, "clientEmail");
    assertResolved(byRef.get("R1-2"), takenOn, "clientRef");
    assertResolved(byRef.get("R1-3"), byIdNumber, "clientIdNumber");
    assertResolved(byRef.get("R1-4"), takenOn, "clientRef");
    assertResolved(byRef.get("R1-5"), thandi, "clientEmail");
  }

  // 2
  @Test
  void commit_createsOrdinaryMatters_leadClientLinkPortalAndAudit_withNoTemplate()
      throws Exception {
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("Smith v Road Accident Fund")
                    .clientEmail(THANDI_EMAIL)
                    .number("MT-2001")
                    .description("Claim for injuries")
                    .dueDate("15/03/2027")
                    .workType("  litigation   matters ")
                    .priority("High")
                    .fileRoom("Basement B")
                    .ref("R2-1"),
                row("Sale of erf 77").clientRef(TAKEN_ON_REF).ref("R2-2")),
            mapping());
    commit(batch);

    var detail = json(detail(mockMvc, owner(), batch.batchId()));
    assertThat((String) JsonPath.read(detail, "$.status")).isEqualTo("COMPLETED");
    assertThat((Integer) JsonPath.read(detail, "$.counts.created")).isEqualTo(2);
    assertThat((List<String>) JsonPath.read(detail, "$.allowedActions"))
        .contains("DOWNLOAD_RESULT");

    var rows = rowsByRef(batch.batchId());
    UUID smith = resultEntity(rows.get("R2-1"));
    UUID sale = resultEntity(rows.get("R2-2"));
    assertThat(resultEntityType(batch.batchId(), "R2-1")).isEqualTo("project");
    var matter = inTenant(() -> projects.findById(smith).orElseThrow());
    assertThat(matter.getName()).isEqualTo("Smith v Road Accident Fund");
    assertThat(matter.getCustomerId()).isEqualTo(thandi);
    assertThat(matter.getReferenceNumber()).isEqualTo("MT-2001");
    assertThat(matter.getDescription()).isEqualTo("Claim for injuries");
    assertThat(matter.getDueDate()).isEqualTo(LocalDate.of(2027, 3, 15));
    assertThat(matter.getWorkType()).isEqualTo("LITIGATION_MATTERS");
    assertThat(matter.getPriority()).isEqualTo(ProjectPriority.HIGH);
    assertThat(matter.getCreatedBy()).isEqualTo(ownerId);
    // the parked column is a note on the matter (D-163)
    assertThat(matter.getCustomFields())
        .containsEntry("takeon_parked", Map.of("File room", "Basement B"));
    assertThat(inTenant(() -> projects.findById(sale).orElseThrow().getCustomerId()))
        .isEqualTo(takenOn);

    for (UUID[] pair : new UUID[][] {{smith, thandi}, {sale, takenOn}}) {
      UUID matterId = pair[0];
      UUID clientId = pair[1];
      // the committer is the only member, and the lead
      assertThat(members(matterId)).isEqualTo(Map.of(ownerId, "lead"));
      // the client link and the portal read model (CustomerProjectLinkedEvent, AFTER_COMMIT)
      assertThat(
              count(
                  "SELECT count(*) FROM %s.customer_projects WHERE customer_id = ? AND project_id = ?",
                  clientId, matterId))
          .isEqualTo(1);
      assertThat(portalReadModel.findProjectsByCustomer(ORG_ID, clientId))
          .extracting(PortalProjectView::id)
          .contains(matterId);
      // no template: no tasks and no checklist for the matter
      assertThat(count("SELECT count(*) FROM %s.tasks WHERE project_id = ?", matterId)).isZero();
      // the ordinary audit of a new matter
      assertThat(TakeOnTestApi.auditEvents(jdbc, schema, matterId))
          .extracting(TakeOnTestApi.AuditRow::eventType)
          .containsExactly("project.created");
    }
    // the commit needed no sign-off: commit_requested carries no sign_off_id (D-90)
    var batchAudit = TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId());
    assertThat(batchAudit)
        .filteredOn(a -> a.eventType().equals("takeon.batch.commit_requested"))
        .singleElement()
        .satisfies(a -> assertThat(a.details()).containsOnlyKeys("batch_id", "dataset"));
    assertThat(
            count("SELECT count(*) FROM %s.take_on_sign_offs WHERE batch_id = ?", batch.batchId()))
        .isZero();
  }

  // 3
  @Test
  void anOrphanOrAMissingClientReference_isAnError_andTheValidRowsStillCommit() throws Exception {
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("Orphan by ID").clientRef("NOPE-404").ref("R3-1"),
                row("Orphan by email").clientEmail("ghost@example.test").ref("R3-2"),
                row("No client at all").ref("R3-3"),
                row("Valid one").clientEmail(THANDI_EMAIL).ref("R3-4")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.error")).isEqualTo(3);
    var rows = rowsByRef(batch.batchId());
    assertError(
        rows.get("R3-1"),
        MattersDatasetHandler.CLIENT_UNRESOLVED,
        "clientRef",
        "No client matches NOPE-404. Take the client on first, or correct the reference.");
    assertError(
        rows.get("R3-2"),
        MattersDatasetHandler.CLIENT_UNRESOLVED,
        "clientEmail",
        "No client matches ghost@example.test. Take the client on first, or correct the"
            + " reference.");
    assertError(
        rows.get("R3-3"),
        "REQUIRED",
        "clientEmail",
        "Enter the client's email, previous-system ID or ID number.");

    commit(batch);
    var after = rowsByRef(batch.batchId());
    assertThat(after.get("R3-4").get("status")).isEqualTo("CREATED");
    for (String ref : List.of("R3-1", "R3-2", "R3-3")) {
      assertThat(after.get(ref).get("status")).isEqualTo("ERROR");
      assertThat(after.get(ref).get("resultEntityId")).isNull();
    }
  }

  // 4
  @Test
  void aClientWhoseLifecycleRefusesAMatter_orAnAmbiguousIdNumber_isAnError() throws Exception {
    UUID prospect =
        UUID.fromString(
            TestEntityHelper.createCustomer(
                mockMvc, ownerJwt(), "Prospect Client", "mt.prospect@example.test"));
    UUID offboarded =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createCustomerWithStatus(
                            "Gone Client",
                            "mt.gone@example.test",
                            ownerId,
                            LifecycleStatus.OFFBOARDED))
                    .getId());
    activeClientWithIdNumber("Twin One", "mt.twin1@example.test", "6601015009082");
    activeClientWithIdNumber("Twin Two", "mt.twin2@example.test", "6601015009082");
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("For a prospect").clientEmail("mt.prospect@example.test").ref("R4-1"),
                row("For a former client").clientEmail("mt.gone@example.test").ref("R4-2"),
                row("For a twin").clientIdNumber("6601015009082").ref("R4-3")),
            mapping());
    var rows = rowsByRef(batch.batchId());
    // the guard's own text, not re-typed (AC-4; raw enum = pre-existing copy, found.md C-9)
    assertError(
        rows.get("R4-1"),
        MattersDatasetHandler.CLIENT_LIFECYCLE,
        "clientEmail",
        "Cannot create matter for client in PROSPECT lifecycle status");
    assertError(
        rows.get("R4-2"),
        MattersDatasetHandler.CLIENT_LIFECYCLE,
        "clientEmail",
        "Cannot create matter for client in OFFBOARDED lifecycle status");
    assertError(
        rows.get("R4-3"),
        MattersDatasetHandler.CLIENT_AMBIGUOUS,
        "clientIdNumber",
        "More than one client has the ID number 6601015009082. Use the client's email or"
            + " previous-system ID instead.");
    assertThat(inTenant(() -> projects.findByCustomerId(prospect))).isEmpty();
    assertThat(inTenant(() -> projects.findByCustomerId(offboarded))).isEmpty();
  }

  // 5
  @Test
  void duplicates_byReferenceOrByNameOnTheClient_areSkipped_andAnInFileRepeatIsAnError()
      throws Exception {
    UUID byReference = createMatter("Existing by reference", thandi, "MT-DUP-1");
    UUID byName = createMatter("Lease renewal", thandi, null);
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("Another name").clientEmail(THANDI_EMAIL).number("mt-dup-1").ref("R5-1"),
                row("  LEASE RENEWAL ").clientEmail(THANDI_EMAIL).ref("R5-2"),
                row("Lease renewal").clientIdNumber(ID_NUMBER).ref("R5-3"),
                row("lease renewal").clientEmail("mt.sipho@example.test").ref("R5-4"),
                row("Created in the meantime")
                    .clientEmail(THANDI_EMAIL)
                    .number("MT-NEW-5")
                    .ref("R5-5")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(4);
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.duplicate")).isEqualTo(2);
    var rows = rowsByRef(batch.batchId());
    assertThat(rows.get("R5-1").get("duplicateOfId")).isEqualTo(byReference.toString());
    assertThat(rows.get("R5-2").get("duplicateOfId")).isEqualTo(byName.toString());
    assertThat(rows.get("R5-3").get("duplicateOfId")).isNull(); // same name, another client
    assertError(
        rows.get("R5-4"),
        "DUPLICATE_IN_FILE",
        "name",
        "Duplicate of row %d in this file".formatted((Integer) rows.get("R5-3").get("rowNumber")));

    // a matter with the same reference is created in the UI before the commit runs
    UUID meantime = createMatter("Created in the UI", thandi, "MT-NEW-5");
    commit(batch);
    var after = rowsByRef(batch.batchId());
    assertThat(after.get("R5-1").get("status")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(resultEntity(after.get("R5-1"))).isEqualTo(byReference);
    assertThat(after.get("R5-2").get("status")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(resultEntity(after.get("R5-2"))).isEqualTo(byName);
    assertThat(after.get("R5-3").get("status")).isEqualTo("CREATED");
    assertThat(after.get("R5-5").get("status")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(resultEntity(after.get("R5-5"))).isEqualTo(meantime);
    assertThat(
            count("SELECT count(*) FROM %s.projects WHERE lower(reference_number) = ?", "mt-new-5"))
        .isEqualTo(1);
  }

  // 6
  @Test
  void theResponsibleAttorney_becomesTheLead_andAnUnknownOneIsAWarning() throws Exception {
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("Lead to the admin")
                    .clientEmail(THANDI_EMAIL)
                    .attorney("MTO_Admin@test.com")
                    .ref("R6-1"),
                row("Lead stays the committer")
                    .clientEmail(THANDI_EMAIL)
                    .attorney(OWNER_EMAIL)
                    .ref("R6-2"),
                row("Lead unknown")
                    .clientEmail(THANDI_EMAIL)
                    .attorney("not.a.member@example.test")
                    .ref("R6-3")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(3);
    var rows = rowsByRef(batch.batchId());
    assertThat(values(rows.get("R6-1"))).containsEntry("leadMemberId", adminId.toString());
    assertWarning(
        rows.get("R6-3"),
        MattersDatasetHandler.LEAD_MEMBER_UNKNOWN,
        "leadMemberEmail",
        "No member of the firm has this email address. The member who commits the take-on will"
            + " be the matter's lead.");
    assertThat(values(rows.get("R6-3"))).doesNotContainKey("leadMemberId");

    commit(batch);
    var after = rowsByRef(batch.batchId());
    UUID toAdmin = resultEntity(after.get("R6-1"));
    assertThat(members(toAdmin)).isEqualTo(Map.of(adminId, "lead", ownerId, "member"));
    assertThat(members(resultEntity(after.get("R6-2")))).isEqualTo(Map.of(ownerId, "lead"));
    assertThat(members(resultEntity(after.get("R6-3")))).isEqualTo(Map.of(ownerId, "lead"));
    assertThat(warnings(after.get("R6-2"))).isEmpty(); // D-162
    // side effect of the lead: the ordinary in-app notification to the added member (C-3)
    assertThat(
            jdbc.queryForList(
                ("SELECT title FROM \"%s\".notifications WHERE recipient_member_id = ?"
                        + " AND reference_project_id = ?")
                    .formatted(schema),
                String.class,
                adminId,
                toAdmin))
        .containsExactly("You were added to matter \"Lead to the admin\"");
    assertThat(
            count(
                "SELECT count(*) FROM %s.audit_events WHERE event_type = 'project_member.added'"
                    + " AND details->>'project_id' = ?",
                toAdmin.toString()))
        .isEqualTo(1);
  }

  // 7
  @Test
  void aLeadWhoLeftBeforeTheCommit_isAWarningOnTheCreatedRow_notAFailure() throws Exception {
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, LEAVER, LEAVER_EMAIL, "Matters Leaver", "member");
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(row("Lead has left").clientEmail(THANDI_EMAIL).attorney(LEAVER_EMAIL).ref("R7-1")),
            mapping());
    assertThat(values(rowsByRef(batch.batchId()).get("R7-1"))).containsKey("leadMemberId");
    mockMvc
        .perform(
            delete("/internal/members/" + LEAVER)
                .param("clerkOrgId", ORG_ID)
                .header("X-API-KEY", TestMemberHelper.API_KEY))
        .andExpect(status().isNoContent());

    commit(batch);
    var row = rowsByRef(batch.batchId()).get("R7-1");
    assertThat(row.get("status")).isEqualTo("CREATED");
    assertWarning(
        row,
        MattersDatasetHandler.LEAD_NOT_APPLIED,
        "leadMemberEmail",
        "The responsible attorney could not be made the matter's lead. The member who committed"
            + " the take-on is the lead; change it on the matter.");
    assertThat(members(resultEntity(row))).isEqualTo(Map.of(ownerId, "lead"));
  }

  // 8
  @Test
  void aCommitThatStopsMidBatch_resumesWhereItStopped_andCreatesEachMatterOnce() throws Exception {
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("Resume one").clientEmail(THANDI_EMAIL).number("MT-RES-1").ref("R8-1"),
                row("Resume two").clientEmail(THANDI_EMAIL).number("MT-RES-2").ref("R8-2"),
                row("Resume three").clientEmail(THANDI_EMAIL).number("MT-RES-3").ref("R8-3")),
            mapping());
    TakeOnTestDrain.commit(commitService, schema, ORG_ID, ownerId, batch.batchId(), batch.hash());
    TakeOnTestDrain.run(drain, schema, ORG_ID, Duration.ZERO); // stops after one row
    var mid = json(detail(mockMvc, owner(), batch.batchId()));
    assertThat((String) JsonPath.read(mid, "$.status")).isEqualTo("COMMITTING");
    assertThat((Integer) JsonPath.read(mid, "$.counts.created")).isEqualTo(1);

    TakeOnTestDrain.run(drain, schema, ORG_ID); // the next run finishes it
    TakeOnTestDrain.run(drain, schema, ORG_ID); // and a redelivered run finds nothing to do
    var done = json(detail(mockMvc, owner(), batch.batchId()));
    assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("COMPLETED");
    assertThat((Integer) JsonPath.read(done, "$.counts.created")).isEqualTo(3);
    for (String number : List.of("MT-RES-1", "MT-RES-2", "MT-RES-3")) {
      assertThat(count("SELECT count(*) FROM %s.projects WHERE reference_number = ?", number))
          .isEqualTo(1);
    }
  }

  // 9
  @Test
  void aClientOffboardedBetweenValidationAndCommit_failsThatRowWithTheGuardsText()
      throws Exception {
    UUID leaving =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Leaving Client", "mt.leaving@example.test", ownerId))
                    .getId());
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("Matter of a leaving client")
                    .clientEmail("mt.leaving@example.test")
                    .ref("R9-1"),
                row("Matter of a staying client").clientEmail(THANDI_EMAIL).ref("R9-2")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(2);
    mockMvc
        .perform(
            post("/api/clients/" + leaving + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"OFFBOARDING\"}"))
        .andExpect(status().isOk());

    commit(batch);
    var after = rowsByRef(batch.batchId());
    assertThat(after.get("R9-1").get("status")).isEqualTo("FAILED");
    assertThat(after.get("R9-1").get("failureReason"))
        .isEqualTo("Cannot create matter for client in OFFBOARDING lifecycle status");
    assertThat(after.get("R9-2").get("status")).isEqualTo("CREATED");
    assertThat(inTenant(() -> projects.findByCustomerId(leaving))).isEmpty();
  }

  // 10 (review F3, D-164 amended by D-179)
  @Test
  void aValidatedDuplicateWhoseReferenceChangesBeforeTheCommit_isCreated() throws Exception {
    UUID existing = createMatter("Renumbered matter", thandi, "MT-CHG-1");
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(row("Renumbered matter").clientEmail(THANDI_EMAIL).number("MT-CHG-1").ref("R12-1")),
            mapping());
    assertThat(rowsByRef(batch.batchId()).get("R12-1").get("duplicateOfId"))
        .isEqualTo(existing.toString());

    // the existing matter is renumbered in the UI before the commit: it no longer has the key
    var body = new HashMap<String, Object>();
    body.put("name", "Renumbered matter");
    body.put("clientId", thandi.toString());
    body.put("referenceNumber", "MT-CHG-1-OLD");
    mockMvc
        .perform(
            put("/api/matters/" + existing)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(body)))
        .andExpect(status().isOk());

    commit(batch);
    var row = rowsByRef(batch.batchId()).get("R12-1");
    assertThat(row.get("status")).isEqualTo("CREATED");
    assertThat(resultEntity(row)).isNotEqualTo(existing);
    assertThat(
            count("SELECT count(*) FROM %s.projects WHERE lower(reference_number) = ?", "mt-chg-1"))
        .isEqualTo(1);
  }

  // 11 (review F2, D-178)
  @Test
  void anonymisingAClient_redactsTheMattersRowsResolvedToIt_andLeavesAnotherClientsRowsAlone()
      throws Exception {
    // a client taken on through the clients take-on, then its matters through the matters take-on
    var clients =
        TakeOnTestApi.validatedClients(
            mockMvc,
            owner(),
            TakeOnClientsFiles.csv(
                TakeOnClientsFiles.row("Anon Matter Client", "mt.anon@example.test")
                    .idNumber("8203035009085")
                    .ref("MT-C-ANON")),
            TakeOnClientsFiles.mapping());
    TakeOnTestApi.attest(mockMvc, owner(), clients.batchId(), clients.hash(), Map.of())
        .andExpect(status().isOk());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, clients.batchId(), clients.hash());
    UUID anon =
        jdbc.queryForObject(
            "SELECT result_entity_id FROM \"%s\".take_on_rows WHERE batch_id = ? AND source_ref = ?"
                .formatted(schema),
            UUID.class,
            clients.batchId(),
            "MT-C-ANON");
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("Anon by email").clientEmail("mt.anon@example.test").ref("R13-1"),
                row("Anon by previous ID").clientRef("MT-C-ANON").number("MT-ANON-2").ref("R13-2"),
                row("Anon by ID number").clientIdNumber("8203035009085").ref("R13-3"),
                row("Thandi keeps hers").clientEmail(THANDI_EMAIL).ref("R13-4")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(4);
    commit(batch);
    assertThat(rowsByRef(batch.batchId()).values())
        .extracting(r -> r.get("status"))
        .containsOnly("CREATED");

    mockMvc
        .perform(
            post("/api/clients/" + anon + "/anonymize")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationName\": \"Anon Matter Client\", \"reason\": \"test\"}"))
        .andExpect(status().isOk());

    // the three matters rows resolved to the anonymised client lose their values (its email,
    // previous-system ID and ID number) exactly as its own clients row does; the shells stay
    for (int rowNumber : List.of(2, 3, 4)) {
      var r = rowByNumber(batch.batchId(), rowNumber);
      assertThat((String) r.get("raw")).as("row %d", rowNumber).isEqualTo("{\"redacted\": true}");
      assertThat((String) r.get("mapped"))
          .as("row %d", rowNumber)
          .isEqualTo("{\"redacted\": true}");
      assertThat(r.get("source_ref")).as("row %d", rowNumber).isNull();
      assertThat(r.get("dedup_key")).as("row %d", rowNumber).isNull();
      assertThat(r.get("status")).as("row %d", rowNumber).isEqualTo("CREATED");
    }
    // the row of another client is untouched
    var other = rowByNumber(batch.batchId(), 5);
    assertThat((String) other.get("raw")).contains(THANDI_EMAIL);
    assertThat((String) other.get("mapped")).contains(thandi.toString());
    assertThat(other.get("source_ref")).isEqualTo("R13-4");
    assertThat(other.get("dedup_key")).isNotNull();
  }

  // 12 (delta review M-1, D-178 amended): a VALIDATED row redacted before its batch commits
  @Test
  void aClientAnonymisedBetweenValidationAndCommit_failsItsRedactedRowWithTheClientGoneText()
      throws Exception {
    UUID gone =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Gone Before Commit", "mt.gone@example.test", ownerId))
                    .getId());
    var batch =
        validatedMatters(
            mockMvc,
            owner(),
            csv(
                row("Matter of a client anonymised")
                    .clientEmail("mt.gone@example.test")
                    .ref("R14-1"),
                row("Thandi first of two").clientEmail(THANDI_EMAIL).ref("R14-2"),
                row("Thandi second of two").clientEmail(THANDI_EMAIL).ref("R14-3")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(3);

    mockMvc
        .perform(
            post("/api/clients/" + gone + "/anonymize")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationName\": \"Gone Before Commit\", \"reason\": \"test\"}"))
        .andExpect(status().isOk());
    var redacted = rowByNumber(batch.batchId(), 2);
    assertThat(redacted.get("status")).isEqualTo("VALIDATED");
    assertThat((String) redacted.get("mapped")).isEqualTo("{\"redacted\": true}");

    commit(batch);
    var failed = rowByNumber(batch.batchId(), 2);
    assertThat(failed.get("status")).isEqualTo("FAILED");
    assertThat(failed.get("failure_reason")).isEqualTo(MattersDatasetHandler.CLIENT_GONE_MESSAGE);
    assertThat((String) failed.get("raw")).isEqualTo("{\"redacted\": true}");
    assertThat((String) failed.get("mapped")).isEqualTo("{\"redacted\": true}");
    assertThat(failed.get("source_ref")).isNull();
    for (int rowNumber : List.of(3, 4)) {
      var r = rowByNumber(batch.batchId(), rowNumber);
      assertThat(r.get("status")).as("row %d", rowNumber).isEqualTo("CREATED");
      assertThat((String) r.get("mapped")).as("row %d", rowNumber).contains(thandi.toString());
    }
    assertThat(inTenant(() -> projects.findByCustomerId(gone))).isEmpty();
  }

  // --- helpers ---

  private Map<String, Object> rowByNumber(UUID batchId, int rowNumber) {
    return jdbc.queryForMap(
        ("SELECT status, raw_values::text AS raw, mapped_values::text AS mapped, dedup_key,"
                + " source_ref, failure_reason FROM \"%s\".take_on_rows WHERE batch_id = ? AND row_number = ?")
            .formatted(schema),
        batchId,
        rowNumber);
  }

  private void commit(Validated batch) {
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, batch.batchId(), batch.hash());
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
    if (referenceNumber != null) {
      body.put("referenceNumber", referenceNumber);
    }
    var result =
        mockMvc
            .perform(
                post("/api/matters")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JsonMapper.builder().build().writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  /** member id → "lead" / "member" on the matter. */
  private Map<UUID, String> members(UUID matterId) {
    return jdbc
        .queryForList(
            "SELECT member_id, project_role FROM \"%s\".project_members WHERE project_id = ?"
                .formatted(schema),
            matterId)
        .stream()
        .collect(
            Collectors.toMap(r -> (UUID) r.get("member_id"), r -> (String) r.get("project_role")));
  }

  private Map<String, Map<String, Object>> rowsByRef(UUID batchId) throws Exception {
    return rowContent(json(rows(mockMvc, owner(), batchId, "size=200"))).stream()
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

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private JwtRequestPostProcessor ownerJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
