package io.b2mash.b2b.b2bstrawman.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.audit.AuditDetails;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Phase 88 625C (U6, G-B2, E.9): {@code GET /api/clients/{id}/activity} — the client's own rows,
 * rows on the entities it owns (checklist instances + items, information requests + items, portal
 * contacts) and rows that name it in {@code details.customer_id}. Real path: firm onboarding
 * template → Start Onboarding (auto-instantiates the checklist) → structural fields filled → every
 * item completed through the API → the last completion auto-activates the client (ONBOARDING →
 * ACTIVE). Every assertion is scoped to the clients this test created (count-bleed rule).
 */
class ClientActivityIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_client_activity_625c";
  private static final String OWNER = "user_ca_owner";
  private static final String MEMBER = "user_ca_member";

  /** TestChecklistHelper.createOnboardingTemplate authors two no-document items. */
  private static final int TEMPLATE_ITEM_COUNT = 2;

  @Autowired private AuditService auditService;

  private String tenantSchema;
  private UUID ownerId;

  @BeforeAll
  void setup() throws Exception {
    // null profile: the legal-za FICA pack has document-gated dependencies that
    // TestChecklistHelper.completeChecklistItems cannot satisfy (OD-10 / R-10).
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Client Activity Firm", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "ca_owner@test.com", "Thandi Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "ca_member@test.com", "Carol Member", "member");
    TestChecklistHelper.createOnboardingTemplate(
        mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), "Client Activity Onboarding");
  }

  @Test
  void onboardedClient_feedShowsLifecycleAndEveryChecklistItemEvent() throws Exception {
    String clientId = onboardToActive("Dlamini");
    List<String> itemIds = checklistItemIds(clientId);
    assertThat(itemIds).hasSize(TEMPLATE_ITEM_COUNT);

    List<Map<String, Object>> rows = content(feed(clientId, 0, 200));
    assertThat(eventTypes(rows))
        .contains(
            "customer.created",
            "customer.lifecycle.transitioned",
            "checklist.instance.created",
            "checklist.instance.completed");

    // One checklist.item.completed row per template item, on exactly this client's items.
    List<Map<String, Object>> itemRows = rowsOfType(rows, "checklist.item.completed");
    assertThat(itemRows).hasSize(TEMPLATE_ITEM_COUNT);
    assertThat(itemRows)
        .extracting(r -> (String) r.get("entityId"))
        .containsExactlyInAnyOrderElementsOf(itemIds);
    // U5/U6 publisher obligation: the stored details name the client.
    assertThat(itemRows)
        .allSatisfy(
            r -> {
              assertThat(r.get("entityType")).isEqualTo("checklist_instance_item");
              assertThat(details(r)).containsEntry(AuditDetails.CUSTOMER_ID, clientId);
              assertThat(r.get("label")).isEqualTo("Checklist item completed");
            });

    // Both lifecycle rows; the ONBOARDING → ACTIVE one comes from the real auto-activation.
    List<Map<String, Object>> lifecycle = rowsOfType(rows, "customer.lifecycle.transitioned");
    assertThat(lifecycle)
        .extracting(r -> (String) r.get("label"))
        .containsOnly("Client lifecycle transitioned");
    assertThat(lifecycle)
        .extracting(r -> details(r).get("newStatus"))
        .contains("ONBOARDING", "ACTIVE");
    assertThat(lifecycle)
        .filteredOn(r -> "ACTIVE".equals(details(r).get("newStatus")))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(details(r)).containsEntry("oldStatus", "ONBOARDING");
              assertThat(details(r)).containsEntry("notes", "All onboarding checklists completed");
            });

    // occurredAt DESC (parse — ISO strings with variable fractional digits do not sort lexically).
    assertThat(rows.stream().map(r -> Instant.parse((String) r.get("occurredAt"))).toList())
        .isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  void skippedAndReopenedItemEvents_nameTheClient() throws Exception {
    // R-5: every item-level emit site (completed / skipped / reopened) writes customer_id.
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String clientId =
        TestEntityHelper.createCustomer(
            mockMvc, owner, "Skip " + UUID.randomUUID(), "ca-" + UUID.randomUUID() + "@test.com");
    TestChecklistHelper.startOnboarding(mockMvc, clientId, owner);
    String itemId = checklistItemIds(clientId).getFirst();
    mockMvc
        .perform(
            put("/api/checklist-items/" + itemId + "/skip")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"not applicable\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(put("/api/checklist-items/" + itemId + "/reopen").with(owner))
        .andExpect(status().isOk());

    List<Map<String, Object>> rows = content(feed(clientId, 0, 200));
    for (String eventType : List.of("checklist.item.skipped", "checklist.item.reopened")) {
      assertThat(rowsOfType(rows, eventType))
          .singleElement()
          .satisfies(
              r -> {
                assertThat(r.get("entityId")).isEqualTo(itemId);
                assertThat(details(r)).containsEntry(AuditDetails.CUSTOMER_ID, clientId);
              });
    }
  }

  @Test
  void legacyItemRowWithoutCustomerId_stillAppearsThroughTheChildBranch() throws Exception {
    String clientId = onboardToActive("Legacy");
    String itemId = checklistItemIds(clientId).getFirst();
    String marker = "Legacy row " + UUID.randomUUID();
    // Pre-625C shape: no customer_id in details — only branch (b) (the child join) can match it.
    logAs(
        "checklist.item.completed",
        "checklist_instance_item",
        UUID.fromString(itemId),
        Map.of("itemName", marker));

    List<Map<String, Object>> rows = content(feed(clientId, 0, 200));
    assertThat(rows)
        .filteredOn(r -> marker.equals(details(r).get("itemName")))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.get("entityId")).isEqualTo(itemId);
              assertThat(details(r)).doesNotContainKey(AuditDetails.CUSTOMER_ID);
            });
  }

  @Test
  void rowsNamingTheClientAppear_rowsNamingAnotherClientDoNot() throws Exception {
    String clientA = onboardToActive("Isolation A");
    String clientB = onboardToActive("Isolation B");
    // Branch (c) only: an invoice.* row on an entity neither client owns, tagged by its publisher.
    UUID feeNoteA = UUID.randomUUID();
    UUID feeNoteB = UUID.randomUUID();
    logAs(
        "invoice.created",
        "invoice",
        feeNoteA,
        AuditDetails.matterScoped(null, UUID.fromString(clientA)));
    logAs(
        "invoice.created",
        "invoice",
        feeNoteB,
        AuditDetails.matterScoped(null, UUID.fromString(clientB)));

    List<Map<String, Object>> rowsA = content(feed(clientA, 0, 200));
    List<Map<String, Object>> rowsB = content(feed(clientB, 0, 200));

    assertThat(rowsA)
        .filteredOn(r -> feeNoteA.toString().equals(r.get("entityId")))
        .singleElement()
        .satisfies(r -> assertThat(r.get("label")).isEqualTo("Fee note"));
    assertThat(rowsB)
        .filteredOn(r -> feeNoteB.toString().equals(r.get("entityId")))
        .singleElement()
        .satisfies(r -> assertThat(r.get("label")).isEqualTo("Fee note"));

    // Neither feed holds a single row of the other client: no audit row id, entity id or
    // customer_id tag of B in A (lifecycle, checklist items, the fee note) — and the reverse.
    assertNoRowsOf(rowsA, rowsB, clientA, clientB, checklistItemIds(clientB));
    assertNoRowsOf(rowsB, rowsA, clientB, clientA, checklistItemIds(clientA));
    assertThat(rowsA).extracting(r -> r.get("entityId")).doesNotContain(feeNoteB.toString());
    assertThat(rowsB).extracting(r -> r.get("entityId")).doesNotContain(feeNoteA.toString());
    // B's real-path rows exist (so the negative assertions above are not vacuous).
    assertThat(rowsB)
        .filteredOn(r -> clientB.equals(details(r).get(AuditDetails.CUSTOMER_ID)))
        .hasSizeGreaterThanOrEqualTo(TEMPLATE_ITEM_COUNT + 1);
  }

  @Test
  void paging_disjointPagesAndTotals() throws Exception {
    String clientId = onboardToActive("Paging");
    String all = feed(clientId, 0, 200);
    int total = ((Number) JsonPath.read(all, "$.page.totalElements")).intValue();
    assertThat(total).isEqualTo(content(all).size()).isGreaterThan(4);

    String page0 = feed(clientId, 0, 2);
    String page1 = feed(clientId, 1, 2);
    assertThat(((Number) JsonPath.read(page0, "$.page.size")).intValue()).isEqualTo(2);
    assertThat(((Number) JsonPath.read(page0, "$.page.number")).intValue()).isZero();
    assertThat(((Number) JsonPath.read(page1, "$.page.number")).intValue()).isEqualTo(1);
    assertThat(((Number) JsonPath.read(page0, "$.page.totalElements")).intValue()).isEqualTo(total);
    assertThat(((Number) JsonPath.read(page0, "$.page.totalPages")).intValue())
        .isEqualTo((total + 1) / 2);

    List<String> ids0 = JsonPath.read(page0, "$.content[*].id");
    List<String> ids1 = JsonPath.read(page1, "$.content[*].id");
    List<String> allIds = JsonPath.read(all, "$.content[*].id");
    assertThat(ids0).hasSize(2);
    assertThat(ids1).hasSize(2).doesNotContainAnyElementsOf(ids0);
    // Pages follow the same occurredAt DESC, id DESC order as the unpaged read.
    assertThat(allIds.subList(0, 4))
        .containsExactly(ids0.get(0), ids0.get(1), ids1.get(0), ids1.get(1));
  }

  @Test
  void memberWithoutTeamOversight_isForbidden() throws Exception {
    String clientId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, OWNER),
            "Forbidden " + UUID.randomUUID(),
            "ca-forbid-" + UUID.randomUUID() + "@test.com");
    mockMvc
        .perform(
            get("/api/clients/" + clientId + "/activity")
                .with(TestJwtFactory.memberJwt(ORG_ID, MEMBER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void unknownClient_is404() throws Exception {
    UUID unknown = UUID.randomUUID();
    mockMvc
        .perform(
            get("/api/clients/" + unknown + "/activity")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Customer not found"))
        .andExpect(jsonPath("$.detail").value("No customer found with id " + unknown));
  }

  // ---- helpers ----

  /**
   * PROSPECT → ONBOARDING (auto-instantiates the onboarding checklist) → structural fields → every
   * item completed; the last completion auto-transitions the client to ACTIVE.
   */
  private String onboardToActive(String name) throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String clientId =
        TestEntityHelper.createCustomer(
            mockMvc,
            owner,
            name + " " + UUID.randomUUID(),
            "ca-" + UUID.randomUUID() + "@test.com");
    TestChecklistHelper.startOnboarding(mockMvc, clientId, owner);
    TestChecklistHelper.fillStructuralPrerequisiteFields(mockMvc, clientId, owner);
    TestChecklistHelper.completeChecklistItems(mockMvc, clientId, owner);
    mockMvc
        .perform(get("/api/clients/" + clientId).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));
    return clientId;
  }

  private List<String> checklistItemIds(String clientId) throws Exception {
    String json =
        mockMvc
            .perform(
                get("/api/clients/" + clientId + "/checklists")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$[*].items[*].id");
  }

  private String feed(String clientId, int page, int size) throws Exception {
    return mockMvc
        .perform(
            get("/api/clients/" + clientId + "/activity")
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static List<Map<String, Object>> content(String body) {
    return JsonPath.read(body, "$.content[*]");
  }

  private static List<String> eventTypes(List<Map<String, Object>> rows) {
    return rows.stream().map(r -> (String) r.get("eventType")).toList();
  }

  private static List<Map<String, Object>> rowsOfType(
      List<Map<String, Object>> rows, String eventType) {
    return rows.stream().filter(r -> eventType.equals(r.get("eventType"))).toList();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> details(Map<String, Object> row) {
    var details = (Map<String, Object>) row.get("details");
    return details != null ? details : Map.of();
  }

  private static void assertNoRowsOf(
      List<Map<String, Object>> rows,
      List<Map<String, Object>> otherRows,
      String self,
      String other,
      List<String> otherItemIds) {
    var otherRowIds = new HashSet<>(otherRows.stream().map(r -> r.get("id")).toList());
    // A row may legitimately appear in both feeds only if it names both clients — none here do.
    assertThat(rows).extracting(r -> r.get("id")).doesNotContainAnyElementsOf(otherRowIds);
    assertThat(rows).extracting(r -> r.get("entityId")).doesNotContain(other);
    assertThat(rows).extracting(r -> r.get("entityId")).doesNotContainAnyElementsOf(otherItemIds);
    assertThat(rows)
        .extracting(r -> details(r).get(AuditDetails.CUSTOMER_ID))
        .filteredOn(Objects::nonNull)
        .containsOnly(self);
  }

  private void logAs(String eventType, String entityType, UUID entityId, Map<String, ?> details) {
    inTenant(
        () -> {
          auditService.log(
              AuditEventBuilder.builder()
                  .eventType(eventType)
                  .entityType(entityType)
                  .entityId(entityId)
                  .details(Map.copyOf(details))
                  .build());
          return null;
        });
  }

  private <T> T inTenant(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(action::get);
  }
}
