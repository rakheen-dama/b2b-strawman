package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.Validated;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 93 657C (task 657.24, §17.3.9, §17.9, FD-4, FD-36): who may use the four trust signer
 * endpoints — the sign-off queue, the review, the sign-off and the signer's own withdrawal. A
 * custom "Partner" role holding only {@code SIGN_OFF_TRUST_TAKE_ON} and {@code VIEW_TRUST} does all
 * four and nothing else of take-on; without {@code VIEW_TRUST} it is refused all four; an admin
 * (who does not inherit the owner-only capability) is refused all four; only the live signer may
 * withdraw a sign-off. Plus the sign-off's refusals. Every batch is the owner's §17.3.13 take-on on
 * fresh accounts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnSignOffAccessIntegrationTest {

  private static final String ORG_ID = "org_takeon_sign_off_access_test";
  private static final String OWNER = "user_tsa_owner";
  private static final String ADMIN = "user_tsa_admin";
  private static final String PARTNER = "user_tsa_partner";
  private static final String PARTNER_NO_VIEW = "user_tsa_partner_noview";
  private static final String FIRM = "Sign-Off Access Attorneys";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;

  private String schema;
  private UUID partnerId;
  private TakeOnTrustWorld world;

  @BeforeAll
  void setup() throws Exception {
    schema = provisioningService.provisionTenant(ORG_ID, FIRM, "legal-za").schemaName();
    UUID ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tsa_owner@test.com", "Access Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, ADMIN, "tsa_admin@test.com", "Access Admin", "admin");
    partnerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, PARTNER, "tsa_partner@test.com", "Pat Partner", "member"));
    UUID noView =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, PARTNER_NO_VIEW, "tsa_noview@test.com", "Nia NoView", "member"));
    world =
        new TakeOnTrustWorld(
            mockMvc, jdbc, commitService, drain, ORG_ID, OWNER, schema, ownerId, "tsa");
    assignRole(partnerId, createRole("Partner", "SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST"));
    assignRole(noView, createRole("Partner without trust view", "SIGN_OFF_TRUST_TAKE_ON"));
    world.takeOnClientsAndMatters(List.of("A", "B", "C"));
  }

  // 1, 2, 3, 4
  @Test
  void aPartnerListsReadsSignsAndWithdraws_withOnlyTheTwoCapabilities() throws Exception {
    var acc = world.freshAccounts();
    Validated batch = world.validated(acc);
    String accountName = accountName(acc.g());

    // 1. the queue
    String queue = json(TakeOnTestApi.signOffQueue(mockMvc, partner()).andExpect(status().isOk()));
    List<Map<String, Object>> items =
        JsonPath.read(queue, "$[?(@.batchId == '%s')]".formatted(batch.batchId()));
    assertThat(items)
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.get("trustAccountId")).isEqualTo(acc.g().toString());
              assertThat(i.get("trustAccountName")).isEqualTo(accountName);
              assertThat(i.get("cutoverDate")).isEqualTo("2026-10-31");
              assertThat(i.get("preparedByName")).isEqualTo("Access Owner");
              assertThat(i.get("validatedAt")).isNotNull();
            });

    // 2. the review: what the partner signs, from this endpoint alone
    String review =
        json(
            TakeOnTestApi.signOffReview(mockMvc, partner(), batch.batchId(), "size=5")
                .andExpect(status().isOk()));
    assertThat((String) JsonPath.read(review, "$.status")).isEqualTo("VALIDATED");
    assertThat((String) JsonPath.read(review, "$.trustAccountName")).isEqualTo(accountName);
    assertThat((String) JsonPath.read(review, "$.validationHash")).isEqualTo(batch.hash());
    assertThat(
            new java.math.BigDecimal(
                JsonPath.read(review, "$.position.cashbookBalance").toString()))
        .isEqualByComparingTo("403500.00");
    assertThat((String) JsonPath.read(review, "$.summary.equation.eq2.difference"))
        .isEqualTo("0.00");
    assertThat((List<String>) JsonPath.read(review, "$.summary.flags"))
        .containsExactly("UNIDENTIFIED_OVER_ONE_YEAR");
    assertThat((Integer) JsonPath.read(review, "$.counts.total")).isEqualTo(9);
    assertThat((List<?>) JsonPath.read(review, "$.rows.content")).hasSize(5);
    assertThat((Integer) JsonPath.read(review, "$.rows.page.totalElements")).isEqualTo(9);
    assertThat((String) JsonPath.read(review, "$.declaration.version"))
        .isEqualTo("TAKEON-SIGNOFF-v1");
    assertThat((String) JsonPath.read(review, "$.declaration.text"))
        .isEqualTo(declaration(accountName));
    assertThat((Object) JsonPath.read(review, "$.liveSignOff")).isNull();
    assertThat((Boolean) JsonPath.read(review, "$.signedByCaller")).isFalse();
    assertThat((List<String>) JsonPath.read(review, "$.allowedActions"))
        .containsExactly("SIGN_OFF");

    // 3. the sign-off
    String signed =
        json(
            TakeOnTestApi.signOff(mockMvc, partner(), batch.batchId(), batch.hash())
                .andExpect(status().isOk()));
    assertThat((String) JsonPath.read(signed, "$.liveSignOff.memberName")).isEqualTo("Pat Partner");
    assertThat((String) JsonPath.read(signed, "$.liveSignOff.roleName")).isEqualTo("Partner");
    assertThat((Boolean) JsonPath.read(signed, "$.signedByCaller")).isTrue();
    assertThat((List<String>) JsonPath.read(signed, "$.allowedActions"))
        .containsExactly("WITHDRAW_SIGN_OFF");
    var stored =
        jdbc.queryForMap(
            ("SELECT id, kind, member_id, member_name, role_name, statement_version,"
                    + " statement_text, validation_hash FROM \"%s\".take_on_sign_offs"
                    + " WHERE batch_id = ? AND revoked_at IS NULL")
                .formatted(schema),
            batch.batchId());
    assertThat(stored)
        .containsEntry("kind", "TRUST_SIGN_OFF")
        .containsEntry("member_id", partnerId)
        .containsEntry("member_name", "Pat Partner")
        .containsEntry("role_name", "Partner")
        .containsEntry("statement_version", "TAKEON-SIGNOFF-v1")
        .containsEntry("statement_text", declaration(accountName))
        .containsEntry("validation_hash", batch.hash());
    var audit =
        TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId()).stream()
            .filter(e -> e.eventType().equals("takeon.batch.signed_off"))
            .findFirst()
            .orElseThrow();
    assertThat(audit.details())
        .containsOnlyKeys("batch_id", "sign_off_id", "trust_account_id", "statement_version")
        .containsEntry("batch_id", batch.batchId().toString())
        .containsEntry("sign_off_id", stored.get("id").toString())
        .containsEntry("trust_account_id", acc.g().toString())
        .containsEntry("statement_version", "TAKEON-SIGNOFF-v1");
    assertThat(queueIds()).doesNotContain(batch.batchId().toString());
    // the owner's view of the batch: COMMIT now, and the revoke path
    assertThat((List<String>) JsonPath.read(world.batchJson(batch.batchId()), "$.allowedActions"))
        .contains("COMMIT", "REVOKE_SIGN_OFF")
        .doesNotContain("SIGN_OFF", "WITHDRAW_SIGN_OFF");

    // 4. the partner withdraws their own sign-off
    String withdrawn =
        json(
            TakeOnTestApi.withdrawSignOff(mockMvc, partner(), batch.batchId())
                .andExpect(status().isOk()));
    assertThat((Object) JsonPath.read(withdrawn, "$.liveSignOff")).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_reason FROM \"%s\".take_on_sign_offs WHERE id = ?"
                    .formatted(schema),
                String.class,
                stored.get("id")))
        .isEqualTo("REVOKED");
    assertThat(TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId()))
        .anySatisfy(
            e -> {
              assertThat(e.eventType()).isEqualTo("takeon.batch.sign_off_revoked");
              assertThat(e.details()).containsEntry("reason_code", "REVOKED");
              assertThat(e.details()).containsEntry("kind", "TRUST_SIGN_OFF");
            });
    assertThat(queueIds()).contains(batch.batchId().toString());
  }

  // 5
  @Test
  void aPartnerIsRefusedEveryTakeOnEndpointBeyondTheFour() throws Exception {
    var batch = world.validated(world.freshAccounts());
    UUID id = batch.batchId();
    denied(mockMvc.perform(get("/api/take-on/batches/" + id).with(partner())));
    denied(mockMvc.perform(get("/api/take-on/batches/" + id + "/rows").with(partner())));
    denied(mockMvc.perform(post("/api/take-on/batches/" + id + "/validate").with(partner())));
    denied(TakeOnTestApi.commit(mockMvc, partner(), id, batch.hash()));
    denied(mockMvc.perform(post("/api/take-on/batches/" + id + "/cancel").with(partner())));
    denied(mockMvc.perform(get("/api/take-on/batches").with(partner())));
    denied(mockMvc.perform(get("/api/take-on/datasets").with(partner())));
    denied(mockMvc.perform(get("/api/take-on/go-live-checklist").with(partner())));
  }

  // 6, 7
  @Test
  void withoutViewTrust_andAsAnAdmin_allFourAreRefused() throws Exception {
    var batch = world.validated(world.freshAccounts());
    for (var jwt : List.of(partnerNoView(), admin())) {
      denied(TakeOnTestApi.signOffQueue(mockMvc, jwt));
      denied(TakeOnTestApi.signOffReview(mockMvc, jwt, batch.batchId(), null));
      denied(TakeOnTestApi.signOff(mockMvc, jwt, batch.batchId(), batch.hash()));
      denied(TakeOnTestApi.withdrawSignOff(mockMvc, jwt, batch.batchId()));
    }
    // an admin preparing the take-on is never offered the signer's actions
    assertThat(
            (List<String>)
                JsonPath.read(
                    json(TakeOnTestApi.detail(mockMvc, admin(), batch.batchId())),
                    "$.allowedActions"))
        .contains("REVALIDATE")
        .doesNotContain("SIGN_OFF", "WITHDRAW_SIGN_OFF", "COMMIT");
    assertThat(signOffCount(batch.batchId())).isZero();
  }

  // 8
  @Test
  void onlyTheSignerWithdraws_whileThePreparersRevokeStillWorks() throws Exception {
    var batch = world.validated(world.freshAccounts());
    world.signOff(batch); // the owner signs
    TakeOnTestApi.withdrawSignOff(mockMvc, partner(), batch.batchId())
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.detail")
                .value("Only the member who signed off can withdraw this sign-off."));
    UUID signOffId =
        jdbc.queryForObject(
            ("SELECT id FROM \"%s\".take_on_sign_offs WHERE batch_id = ? AND revoked_at IS NULL")
                .formatted(schema),
            UUID.class,
            batch.batchId());
    // D-113: the admin's revoke on the MANAGE_TAKE_ON path
    TakeOnTestApi.revokeSignOff(mockMvc, admin(), batch.batchId(), signOffId)
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_reason FROM \"%s\".take_on_sign_offs WHERE id = ?"
                    .formatted(schema),
                String.class,
                signOffId))
        .isEqualTo("REVOKED");
  }

  // refusals
  @Test
  void theSignOffRefusesWhatCannotBeSigned() throws Exception {
    var batch = world.validated(world.freshAccounts());
    // the declaration must be accepted, in its current version
    TakeOnTestApi.signOff(
            mockMvc, partner(), batch.batchId(), batch.hash(), "TAKEON-SIGNOFF-v1", false)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TAKE_ON_DECLARATION_REQUIRED"))
        .andExpect(jsonPath("$.detail").value("Accept the declaration to sign off."));
    TakeOnTestApi.signOff(
            mockMvc, partner(), batch.batchId(), batch.hash(), "TAKEON-SIGNOFF-v0", true)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Sign off with the current declaration (TAKEON-SIGNOFF-v1)."));
    // a second sign-off
    TakeOnTestApi.signOff(mockMvc, partner(), batch.batchId(), batch.hash())
        .andExpect(status().isOk());
    TakeOnTestApi.signOff(mockMvc, world.owner(), batch.batchId(), batch.hash())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_ALREADY_SIGNED_OFF"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "This trust take-on has already been signed off. Withdraw the sign-off to"
                        + " sign again."));

    // a blocked batch (client C in debit)
    var acc = world.freshAccounts();
    var blocked =
        world.validated(
            acc.g(),
            TakeOnTrustFiles.withRows(
                world.files(acc),
                TakeOnTrustFiles.CLIENT_LEDGER,
                List.of("A,A1,300000.00,", "B,B1,150000.00,", "C,C1,-49500.00,")),
            TakeOnTrustFiles.section17313Position(TakeOnTrustWorld.C));
    TakeOnTestApi.signOff(mockMvc, partner(), blocked.batchId(), blocked.hash())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_BLOCKED"));
    assertThat(queueIds()).doesNotContain(blocked.batchId().toString());

    // a clients batch is not a trust take-on
    var clients =
        TakeOnTestApi.validatedClients(
            mockMvc,
            world.owner(),
            TakeOnClientsFiles.csv(TakeOnClientsFiles.row("Zed Client", "tsa.zed@example.test")),
            TakeOnClientsFiles.mapping());
    TakeOnTestApi.signOff(mockMvc, partner(), clients.batchId(), clients.hash())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_WRONG_STATE"))
        .andExpect(jsonPath("$.detail").value("Only a trust take-on is signed off."));
  }

  // --- helpers ---

  private String declaration(String accountName) {
    return "I am a partner or director of "
        + FIRM
        + " responsible for its trust accounting records (Rule 54.19). I have reviewed this"
        + " take-on of "
        + accountName
        + " at 31 October 2026. The trust creditors listed agree, to the cent, with the reconciled"
        + " trust position in the previous system at that date, and no trust creditor is in"
        + " debit.";
  }

  private List<String> queueIds() throws Exception {
    return JsonPath.read(
        json(TakeOnTestApi.signOffQueue(mockMvc, partner()).andExpect(status().isOk())),
        "$[*].batchId");
  }

  private String accountName(UUID account) {
    return jdbc.queryForObject(
        "SELECT account_name FROM \"%s\".trust_accounts WHERE id = ?".formatted(schema),
        String.class,
        account);
  }

  private long signOffCount(UUID batchId) {
    Long n =
        jdbc.queryForObject(
            "SELECT count(*) FROM \"%s\".take_on_sign_offs WHERE batch_id = ?".formatted(schema),
            Long.class,
            batchId);
    return n == null ? 0 : n;
  }

  private static void denied(ResultActions result) throws Exception {
    result.andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("Access denied"));
  }

  private String createRole(String name, String... capabilities) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(world.owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":%s}"""
                            .formatted(
                                name,
                                JsonMapper.builder()
                                    .build()
                                    .writeValueAsString(List.of(capabilities)))))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(UUID memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(world.owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private RequestPostProcessor partner() {
    return TestJwtFactory.customRoleJwt(ORG_ID, PARTNER);
  }

  private RequestPostProcessor partnerNoView() {
    return TestJwtFactory.customRoleJwt(ORG_ID, PARTNER_NO_VIEW);
  }

  private RequestPostProcessor admin() {
    return TestJwtFactory.adminJwt(ORG_ID, ADMIN);
  }
}
