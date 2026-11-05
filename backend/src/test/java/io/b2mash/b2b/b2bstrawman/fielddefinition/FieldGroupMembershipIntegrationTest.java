package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F3 (§14.3.16, R-E5a): field-group membership travels on {@code POST/PUT /api/field-groups} as
 * {@code fieldDefinitionIds} and is returned on every {@code FieldGroupResponse}. The PUT diffs
 * membership through the granular add/remove/reorder paths (validation + audit) in one transaction;
 * {@code null} leaves membership unchanged. Pack-seeded groups are editable and the ADR-356 {@code
 * membership} reconcile never re-adds a field the firm removed.
 *
 * <p>Membership is read back through the pre-existing granular {@code GET
 * /api/field-groups/{id}/fields} so the assertions pin behaviour, not only the new response field.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldGroupMembershipIntegrationTest {

  private static final String ORG_ID = "org_fg_membership_test";
  private static final String OWNER = "user_fgmem_owner";
  private static final String CONVEYANCING_PACK = "conveyancing-za-project";
  private static final String CONVEYANCING_GROUP = "conveyancing_za_matter";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldPackSeeder fieldPackSeeder;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Field Group Membership Test Org", "legal-za");
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "fgmem_owner@test.com", "FG Membership Owner", "owner"));
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void put_withAB_setsMembersAB() throws Exception {
    String groupId = createGroup("PROJECT", unique("Mem AB Group"), null);
    String a = createField("PROJECT", unique("Mem AB A"));
    String b = createField("PROJECT", unique("Mem AB B"));

    var result =
        putGroup(groupId, "{\"name\": \"Mem AB Group\", \"sortOrder\": 0, %s}", ids(a, b))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(readIds(result, "$.fieldDefinitionIds")).containsExactly(a, b);
    mockMvc
        .perform(get("/api/field-groups/" + groupId + "/fields").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fieldDefinitionId").value(a))
        .andExpect(jsonPath("$[0].sortOrder").value(0))
        .andExpect(jsonPath("$[1].fieldDefinitionId").value(b))
        .andExpect(jsonPath("$[1].sortOrder").value(1));
  }

  @Test
  void put_withBC_afterAB_replacesInOrder() throws Exception {
    String groupId = createGroup("PROJECT", unique("Mem BC Group"), null);
    String a = createField("PROJECT", unique("Mem BC A"));
    String b = createField("PROJECT", unique("Mem BC B"));
    String c = createField("PROJECT", unique("Mem BC C"));

    putGroup(groupId, "{\"name\": \"Mem BC Group\", \"sortOrder\": 0, %s}", ids(a, b))
        .andExpect(status().isOk());
    putGroup(groupId, "{\"name\": \"Mem BC Group\", \"sortOrder\": 0, %s}", ids(b, c))
        .andExpect(status().isOk());

    assertThat(memberIds(groupId)).containsExactly(b, c);
    mockMvc
        .perform(get("/api/field-groups/" + groupId + "/fields").with(owner()))
        .andExpect(jsonPath("$[0].sortOrder").value(0))
        .andExpect(jsonPath("$[1].sortOrder").value(1));

    // Pure reorder
    putGroup(groupId, "{\"name\": \"Mem BC Group\", \"sortOrder\": 0, %s}", ids(c, b))
        .andExpect(status().isOk());
    assertThat(memberIds(groupId)).containsExactly(c, b);
  }

  @Test
  void put_withoutField_leavesMembershipUnchanged() throws Exception {
    String groupId = createGroup("TASK", unique("Mem Null Group"), null);
    String a = createField("TASK", unique("Mem Null A"));
    String b = createField("TASK", unique("Mem Null B"));

    putGroup(groupId, "{\"name\": \"Mem Null Group\", \"sortOrder\": 0, %s}", ids(a, b))
        .andExpect(status().isOk());

    // No fieldDefinitionIds key → membership untouched
    var result =
        mockMvc
            .perform(
                put("/api/field-groups/" + groupId)
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Mem Null Group Renamed\", \"sortOrder\": 0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Mem Null Group Renamed"))
            .andReturn();
    assertThat(readIds(result, "$.fieldDefinitionIds")).containsExactly(a, b);
    assertThat(memberIds(groupId)).containsExactly(a, b);

    // Explicit empty list clears
    putGroup(groupId, "{\"name\": \"Mem Null Group\", \"sortOrder\": 0, %s}", ids())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fieldDefinitionIds").isEmpty());
    assertThat(memberIds(groupId)).isEmpty();
  }

  @Test
  void create_withFieldDefinitionIds_createsMembers() throws Exception {
    String a = createField("PROJECT", unique("Mem Create A"));
    String b = createField("PROJECT", unique("Mem Create B"));

    var created =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"entityType\": \"PROJECT\", \"name\": \"%s\", \"sortOrder\": 0, %s}"
                            .formatted(unique("Mem Create Group"), ids(b, a))))
            .andExpect(status().isCreated())
            .andReturn();
    String groupId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    assertThat(readIds(created, "$.fieldDefinitionIds")).containsExactly(b, a);
    assertThat(memberIds(groupId)).containsExactly(b, a);

    // Without the key → an empty group
    mockMvc
        .perform(
            post("/api/field-groups")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entityType\": \"PROJECT\", \"name\": \"%s\", \"sortOrder\": 0}"
                        .formatted(unique("Mem Create Empty"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fieldDefinitionIds").isArray())
        .andExpect(jsonPath("$.fieldDefinitionIds").isEmpty());
  }

  @Test
  void put_auditsOneUpdatedRowWithCounts() throws Exception {
    String groupId = createGroup("PROJECT", unique("Mem Audit Group"), null);
    String a = createField("PROJECT", unique("Mem Audit A"));
    String b = createField("PROJECT", unique("Mem Audit B"));
    String c = createField("PROJECT", unique("Mem Audit C"));

    putGroup(groupId, "{\"name\": \"Mem Audit Group\", \"sortOrder\": 0, %s}", ids(a, b))
        .andExpect(status().isOk());
    putGroup(groupId, "{\"name\": \"Mem Audit Group\", \"sortOrder\": 0, %s}", ids(b, c))
        .andExpect(status().isOk());

    List<Map<String, Object>> details =
        inTenantTx(
            () ->
                auditEventRepository
                    .findByFilter(
                        "field_group",
                        UUID.fromString(groupId),
                        null,
                        "field_group.updated",
                        null,
                        null,
                        Pageable.ofSize(10))
                    .getContent()
                    .stream()
                    .map(AuditEvent::getDetails)
                    .collect(Collectors.toCollection(ArrayList::new)));

    // Exactly one field_group.updated row per PUT (id-scoped — no count-bleed)
    assertThat(details).hasSize(2);
    assertThat(details).allSatisfy(d -> assertThat(d).containsEntry("name", "Mem Audit Group"));
    assertThat(details)
        .extracting(d -> String.valueOf(d.get("added")) + "/" + String.valueOf(d.get("removed")))
        .containsExactlyInAnyOrder("2/0", "1/1");
  }

  @Test
  void packSeededGroup_isEditable_andReconcileStaysAdditive() throws Exception {
    UUID groupId =
        inTenantTx(
            () ->
                fieldGroupRepository
                    .findByEntityTypeAndSlug(EntityType.PROJECT, CONVEYANCING_GROUP)
                    .orElseThrow()
                    .getId());
    String sellerId =
        inTenantTx(
            () ->
                fieldDefinitionRepository
                    .findByEntityTypeAndSlug(EntityType.PROJECT, "seller_name")
                    .orElseThrow()
                    .getId()
                    .toString());
    List<String> before = memberIds(groupId.toString());
    assertThat(before).contains(sellerId);

    var groupJson =
        mockMvc
            .perform(get("/api/field-groups/" + groupId).with(owner()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packId").value(CONVEYANCING_PACK))
            .andReturn();
    assertThat(readIds(groupJson, "$.fieldDefinitionIds")).containsExactlyElementsOf(before);
    String body = groupJson.getResponse().getContentAsString();
    String name = JsonPath.read(body, "$.name");
    String description = JsonPath.read(body, "$.description");
    Integer sortOrder = JsonPath.read(body, "$.sortOrder");

    var expected = new ArrayList<>(before);
    expected.remove(sellerId);
    var updated =
        mockMvc
            .perform(
                put("/api/field-groups/" + groupId)
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": %s, \"description\": %s, \"sortOrder\": %d, %s}"
                            .formatted(
                                jsonString(name),
                                jsonString(description),
                                sortOrder,
                                ids(expected.toArray(String[]::new)))))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readIds(updated, "$.fieldDefinitionIds")).containsExactlyElementsOf(expected);

    // Simulate a pre-v3 tenant and re-run the field-pack reconcile (ADR-356 membership step)
    String ledger =
        inTenantTx(
            () -> {
              var current = packInstallRepository.findByPackId(CONVEYANCING_PACK).orElseThrow();
              String version = current.getPackVersion();
              assertThat(
                      packInstallRepository.compareAndSetVersion(CONVEYANCING_PACK, version, "2"))
                  .isEqualTo(1);
              return version;
            });
    List<String> logs = captureLogs(() -> fieldPackSeeder.seedPacksForTenant(schema, ORG_ID));

    assertThat(memberIds(groupId.toString())).containsExactlyElementsOf(expected);
    assertThat(memberIds(groupId.toString())).doesNotContain(sellerId);
    inTenantTx(
        () -> {
          var seller =
              fieldDefinitionRepository
                  .findByEntityTypeAndSlug(EntityType.PROJECT, "seller_name")
                  .orElseThrow();
          assertThat(seller.getId().toString()).isEqualTo(sellerId);
          assertThat(seller.getPackId()).isEqualTo(CONVEYANCING_PACK);
          assertThat(packInstallRepository.findByPackId(CONVEYANCING_PACK).orElseThrow())
              .extracting(p -> p.getPackVersion())
              .isEqualTo(ledger);
          return null;
        });
    assertThat(logs)
        .anySatisfy(
            line ->
                assertThat(line).contains("Reconcile skipped (edited)").contains("'seller_name'"));
  }

  @Test
  void responseShape_everyProducerCarriesFieldDefinitionIds() throws Exception {
    String groupId = createGroup("PROJECT", unique("Mem Shape Group"), null);
    String a = createField("PROJECT", unique("Mem Shape A"));
    String b = createField("PROJECT", unique("Mem Shape B"));

    putGroup(groupId, "{\"name\": \"Mem Shape Group\", \"sortOrder\": 0, %s}", ids(a, b))
        .andExpect(status().isOk());

    var list =
        mockMvc
            .perform(get("/api/field-groups").param("entityType", "PROJECT").with(owner()))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readIds(list, "$[?(@.id=='" + groupId + "')].fieldDefinitionIds[*]"))
        .containsExactly(a, b);

    var byId =
        mockMvc
            .perform(get("/api/field-groups/" + groupId).with(owner()))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readIds(byId, "$.fieldDefinitionIds")).containsExactly(a, b);

    var toggled =
        mockMvc
            .perform(
                patch("/api/field-groups/" + groupId + "/auto-apply")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"autoApply\": false}"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(readIds(toggled, "$.fieldDefinitionIds")).containsExactly(a, b);
  }

  @Test
  void put_rejectsBadMembership_andRollsBack() throws Exception {
    String originalName = unique("Mem Reject Group");
    String groupId = createGroup("PROJECT", originalName, null);
    String a = createField("PROJECT", unique("Mem Reject A"));
    String b = createField("PROJECT", unique("Mem Reject B"));
    String taskField = createField("TASK", unique("Mem Reject Task"));

    putGroup(
            groupId,
            "{\"name\": %s, \"sortOrder\": 0, %%s}".formatted(jsonString(originalName)),
            ids(a))
        .andExpect(status().isOk());

    // Entity-type mismatch → 400, metadata + membership rolled back
    putGroup(groupId, "{\"name\": \"Mem Reject Renamed\", \"sortOrder\": 0, %s}", ids(b, taskField))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Entity type mismatch"));
    mockMvc
        .perform(get("/api/field-groups/" + groupId).with(owner()))
        .andExpect(jsonPath("$.name").value(originalName));
    assertThat(memberIds(groupId)).containsExactly(a);

    // Duplicate id in the list → 400 before any write
    putGroup(groupId, "{\"name\": \"Mem Reject Renamed\", \"sortOrder\": 0, %s}", ids(b, b))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Duplicate field"));
    mockMvc
        .perform(get("/api/field-groups/" + groupId).with(owner()))
        .andExpect(jsonPath("$.name").value(originalName));
    assertThat(memberIds(groupId)).containsExactly(a);

    // A null element → 400 (bean validation), not a 500
    mockMvc
        .perform(
            put("/api/field-groups/" + groupId)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Mem Reject Renamed\", \"sortOrder\": 0, \"fieldDefinitionIds\":"
                        + " [null]}"))
        .andExpect(status().isBadRequest());
    assertThat(memberIds(groupId)).containsExactly(a);
  }

  // --- helpers ---

  private ResultActions putGroup(String groupId, String bodyTemplate, String idsFragment)
      throws Exception {
    return mockMvc.perform(
        put("/api/field-groups/" + groupId)
            .with(owner())
            .contentType(MediaType.APPLICATION_JSON)
            .content(bodyTemplate.formatted(idsFragment)));
  }

  /** The {@code "fieldDefinitionIds": [...]} JSON fragment. */
  private static String ids(String... ids) {
    return "\"fieldDefinitionIds\": ["
        + Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(","))
        + "]";
  }

  private static String jsonString(String value) {
    return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
  }

  private static String unique(String name) {
    return name + " " + UUID.randomUUID().toString().substring(0, 8);
  }

  private static List<String> readIds(MvcResult result, String path) throws Exception {
    List<String> ids = JsonPath.read(result.getResponse().getContentAsString(), path);
    return ids;
  }

  /** Member field ids in sort order, via the pre-existing granular endpoint. */
  private List<String> memberIds(String groupId) throws Exception {
    var result =
        mockMvc
            .perform(get("/api/field-groups/" + groupId + "/fields").with(owner()))
            .andExpect(status().isOk())
            .andReturn();
    return readIds(result, "$[*].fieldDefinitionId");
  }

  private String createGroup(String entityType, String name, String idsFragment) throws Exception {
    String body =
        idsFragment == null
            ? "{\"entityType\": \"%s\", \"name\": %s, \"sortOrder\": 0}"
                .formatted(entityType, jsonString(name))
            : "{\"entityType\": \"%s\", \"name\": %s, \"sortOrder\": 0, %s}"
                .formatted(entityType, jsonString(name), idsFragment);
    var result =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createField(String entityType, String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "%s",
                          "name": "%s",
                          "fieldType": "TEXT",
                          "required": false,
                          "sortOrder": 0
                        }
                        """
                            .formatted(entityType, name)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }

  private static List<String> captureLogs(Runnable action) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(FieldPackSeeder.class);
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
    try {
      action.run();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(null);
    }
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
