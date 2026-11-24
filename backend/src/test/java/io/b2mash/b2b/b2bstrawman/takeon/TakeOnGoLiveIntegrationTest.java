package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnGoLiveService.TakeOnGoLiveAcknowledgeRequest;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 93 656A (task 656.5, §17.3.14, §17.4.2, §17.6.7): the go-live checklist through its three
 * endpoints. The four items list in X3 order with their X3 text; an acknowledgement records who,
 * when and an optional note, and a second one replaces it; a withdrawal deletes it; each act is
 * audited with exactly {@code {item_key}}; every endpoint needs {@code MANAGE_TAKE_ON}. The
 * checklist gates nothing — the matters and clients commits run with no item acknowledged ({@code
 * MattersTakeOnIntegrationTest}, {@code ClientsTakeOnIntegrationTest}). Each test owns its own
 * item, so the tests never share state (the concurrency test borrows the refusals test's item and
 * withdraws it again; the refusals test only needs it unacknowledged).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnGoLiveIntegrationTest {

  private static final String ORG_ID = "org_takeon_go_live_test";
  private static final String OWNER = "user_glt_owner";
  private static final String ADMIN = "user_glt_admin";
  private static final String MEMBER = "user_glt_member";
  private static final String URL = "/api/take-on/go-live-checklist";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnGoLiveService goLive;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;
  private UUID ownerId;
  private UUID adminId;
  private String freshList;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService.provisionTenant(ORG_ID, "Go-live Attorneys", "legal-za").schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "glt_owner@test.com", "Go-live Owner", "owner"));
    adminId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, ADMIN, "glt_admin@test.com", "Go-live Admin", "admin"));
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, MEMBER, "glt_member@test.com", "Go-live Member", "member");
    freshList = body(mockMvc.perform(get(URL).with(owner())).andExpect(status().isOk()));
  }

  // 1
  @Test
  void theChecklist_listsTheFourItemsInX3Order_withTheirTextVerbatim_noneAcknowledged() {
    List<Map<String, Object>> items = JsonPath.read(freshList, "$");
    assertThat(items)
        .extracting(i -> i.get("itemKey"))
        .containsExactly(
            "TRUST_RECORDS_RETAINED",
            "FICA_RECORDS_RETAINED",
            "ARCHIVE_HOSTING_CONSIDERED",
            "AUDITOR_INFORMED");
    assertThat(items)
        .extracting(i -> i.get("text"))
        .containsExactly(
            "Trust records from the previous system will be kept for at least seven years from"
                + " the last entry, secured and immediately accessible to the firm and the Council"
                + " (Rules 54.9.1 and 54.9.2).",
            "FICA records from the previous system will be kept for at least five years after"
                + " each business relationship ends (section 23).",
            "If the previous vendor keeps hosting the archive, or its location changes, we have"
                + " considered whether Council consent (Rule 54.9.2) or a notification to the FIC"
                + " (section 24(3)) is needed. Kazi does not file either.",
            "Our trust auditor has been told the cutover date and will receive the take-on"
                + " report.");
    for (var item : items) {
      assertThat(item)
          .containsOnlyKeys(
              "itemKey",
              "text",
              "acknowledged",
              "acknowledgedBy",
              "acknowledgedByName",
              "acknowledgedAt",
              "note")
          .containsEntry("acknowledged", false)
          .containsEntry("acknowledgedBy", null)
          .containsEntry("acknowledgedByName", null)
          .containsEntry("acknowledgedAt", null)
          .containsEntry("note", null);
    }
  }

  // 2
  @Test
  void acknowledging_recordsWhoWhenAndTheNote_andASecondAcknowledgementReplacesIt()
      throws Exception {
    String first =
        body(
            acknowledge(owner(), "TRUST_RECORDS_RETAINED", "{\"note\":\"  Kept by Archivo  \"}")
                .andExpect(status().isOk()));
    var trust = item(first, "TRUST_RECORDS_RETAINED");
    assertThat(trust)
        .containsEntry("acknowledged", true)
        .containsEntry("acknowledgedBy", ownerId.toString())
        .containsEntry("acknowledgedByName", "Go-live Owner")
        .containsEntry("note", "Kept by Archivo");
    assertThat(trust.get("acknowledgedAt")).isNotNull();

    // again, by another member, without a body: the acknowledgement is replaced (D-166)
    String second =
        body(
            mockMvc
                .perform(put(URL + "/TRUST_RECORDS_RETAINED").with(admin()))
                .andExpect(status().isOk()));
    assertThat(item(second, "TRUST_RECORDS_RETAINED"))
        .containsEntry("acknowledged", true)
        .containsEntry("acknowledgedBy", adminId.toString())
        .containsEntry("acknowledgedByName", "Go-live Admin")
        .containsEntry("note", null);
    // a blank note is no note; the read matches the write
    acknowledge(admin(), "TRUST_RECORDS_RETAINED", "{\"note\":\"   \"}").andExpect(status().isOk());
    assertThat(
            item(
                body(mockMvc.perform(get(URL).with(owner())).andExpect(status().isOk())),
                "TRUST_RECORDS_RETAINED"))
        .containsEntry("note", null)
        .containsEntry("acknowledgedBy", adminId.toString());
    assertThat(rowCount("TRUST_RECORDS_RETAINED")).isEqualTo(1);
  }

  // 3
  @Test
  void withdrawing_deletesTheAcknowledgement_andWithdrawingNothingIsNotFound() throws Exception {
    acknowledge(owner(), "FICA_RECORDS_RETAINED", "{\"note\":\"Five years\"}")
        .andExpect(status().isOk());
    String after =
        body(
            mockMvc
                .perform(delete(URL + "/FICA_RECORDS_RETAINED").with(owner()))
                .andExpect(status().isOk()));
    assertThat(item(after, "FICA_RECORDS_RETAINED"))
        .containsEntry("acknowledged", false)
        .containsEntry("acknowledgedBy", null)
        .containsEntry("note", null);
    assertThat(rowCount("FICA_RECORDS_RETAINED")).isZero();

    mockMvc
        .perform(delete(URL + "/FICA_RECORDS_RETAINED").with(owner()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Go-live item not acknowledged"))
        .andExpect(
            jsonPath("$.detail")
                .value("This item has not been acknowledged, so there is nothing to withdraw."));
  }

  // 4
  @Test
  void eachAct_isAuditedWithExactlyTheItemKey() throws Exception {
    acknowledge(owner(), "ARCHIVE_HOSTING_CONSIDERED", "{\"note\":\"Vendor keeps the archive\"}")
        .andExpect(status().isOk());
    mockMvc
        .perform(delete(URL + "/ARCHIVE_HOSTING_CONSIDERED").with(owner()))
        .andExpect(status().isOk());

    UUID entityId = GoLiveItem.ARCHIVE_HOSTING_CONSIDERED.auditEntityId();
    var audit = TakeOnTestApi.auditEvents(jdbc, schema, entityId);
    assertThat(audit)
        .extracting(TakeOnTestApi.AuditRow::eventType)
        .containsExactly("takeon.go_live.acknowledged", "takeon.go_live.withdrawn");
    for (var row : audit) {
      assertThat(row.details()).isEqualTo(Map.of("item_key", "ARCHIVE_HOSTING_CONSIDERED"));
      assertThat(row.actorType()).isEqualTo("USER");
      assertThat(row.actorName()).isEqualTo("Go-live Owner");
    }
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT entity_type FROM \"%s\".audit_events WHERE entity_id = ?"
                    .formatted(schema),
                String.class,
                entityId))
        .containsExactly("take_on_go_live_item");
    // the note is never audited (PII-safe details, D-93)
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".audit_events WHERE details::text LIKE"
                        + " '%%Vendor keeps%%'")
                    .formatted(schema),
                Integer.class))
        .isZero();
  }

  // 5
  @Test
  void everyEndpointNeedsManageTakeOn_andAnUnknownItemOrALongNoteIsRefused() throws Exception {
    for (var refused :
        List.of(
            mockMvc.perform(get(URL).with(member())),
            acknowledge(member(), "AUDITOR_INFORMED", "{}"),
            mockMvc.perform(delete(URL + "/AUDITOR_INFORMED").with(member())))) {
      refused
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.title").value("Access denied"))
          .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
    }
    assertThat(rowCount("AUDITOR_INFORMED")).isZero();

    acknowledge(owner(), "NOT_AN_ITEM", "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid parameter value"))
        .andExpect(jsonPath("$.detail").value("Parameter 'itemKey' should be of type GoLiveItem"));
    acknowledge(owner(), "AUDITOR_INFORMED", "{\"note\":\"" + "x".repeat(1001) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("note"));
    assertThat(rowCount("AUDITOR_INFORMED")).isZero();
  }

  // 6 (review F4, D-180)
  @Test
  void twoFirstAcknowledgementsAtOnce_bothSucceed_andLeaveOneRow() throws Exception {
    var item = GoLiveItem.AUDITOR_INFORMED;
    var firstWritten = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      // the first acknowledgement writes its row and holds its transaction open …
      Future<?> first =
          executor.submit(
              () ->
                  asMember(
                      ownerId,
                      "owner",
                      () ->
                          transactionTemplate.executeWithoutResult(
                              tx -> {
                                goLive.acknowledge(
                                    item, new TakeOnGoLiveAcknowledgeRequest("First"));
                                firstWritten.countDown();
                                await(release);
                              })));
      assertThat(firstWritten.await(20, TimeUnit.SECONDS)).isTrue();
      // … the second starts while that row is uncommitted (it cannot see it) and waits on its key
      Future<?> second =
          executor.submit(
              () ->
                  asMember(
                      adminId,
                      "admin",
                      () ->
                          goLive.acknowledge(item, new TakeOnGoLiveAcknowledgeRequest("Second"))));
      awaitALockWaitOnTheChecklist();
      release.countDown();
      first.get(30, TimeUnit.SECONDS);
      second.get(30, TimeUnit.SECONDS); // a key violation here would be the 500 (review F4)
    } finally {
      release.countDown();
      executor.shutdownNow();
    }

    assertThat(rowCount("AUDITOR_INFORMED")).isEqualTo(1);
    assertThat(
            item(
                body(mockMvc.perform(get(URL).with(owner())).andExpect(status().isOk())),
                "AUDITOR_INFORMED"))
        .containsEntry("acknowledgedBy", adminId.toString())
        .containsEntry("note", "Second");
    assertThat(TakeOnTestApi.auditEvents(jdbc, schema, item.auditEntityId()))
        .extracting(TakeOnTestApi.AuditRow::eventType)
        .containsExactly("takeon.go_live.acknowledged", "takeon.go_live.acknowledged");

    // give the item back unacknowledged (the refusals test needs it so)
    mockMvc.perform(delete(URL + "/AUDITOR_INFORMED").with(owner())).andExpect(status().isOk());
  }

  // --- helpers ---

  private void asMember(UUID memberId, String role, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, role)
        .run(action);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch not released");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Waits until a session is blocked on a lock while writing the checklist (at most 20 s). */
  private void awaitALockWaitOnTheChecklist() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < deadline) {
      Integer waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE pid <> pg_backend_pid()"
                  + " AND wait_event_type = 'Lock' AND query ILIKE '%take_on_go_live_items%'",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("the second acknowledgement never waited on the first");
  }

  private ResultActions acknowledge(RequestPostProcessor jwt, String key, String json)
      throws Exception {
    return mockMvc.perform(
        put(URL + "/" + key).with(jwt).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private static Map<String, Object> item(String json, String key) {
    List<Map<String, Object>> items = JsonPath.read(json, "$[?(@.itemKey == '" + key + "')]");
    assertThat(items).hasSize(1);
    return items.getFirst();
  }

  private int rowCount(String key) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".take_on_go_live_items WHERE item_key = ?".formatted(schema),
        Integer.class,
        key);
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private RequestPostProcessor admin() {
    return TestJwtFactory.adminJwt(ORG_ID, ADMIN);
  }

  private RequestPostProcessor member() {
    return TestJwtFactory.memberJwt(ORG_ID, MEMBER);
  }
}
