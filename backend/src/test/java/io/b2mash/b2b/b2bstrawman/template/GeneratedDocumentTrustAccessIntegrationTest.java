package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.tool.DocumentTools;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * G89-F52 (D18/D19): a stored document that prints trust money must not be downloadable by a member
 * without {@code VIEW_TRUST}. The generation-time trust filter (PR #1749) only protects a document
 * the member generates; the owner's Statement of Account / Client Trust Statement was downloadable
 * by any member who could see the matter (the system "member" role holds VIEW_TRUST, so the leak is
 * a custom role without it).
 *
 * <p>Fixture: trust accounting on; a client with a trust deposit on a matter; member M on the
 * matter with a custom role (VIEW_LEGAL + GENERATE_STATEMENT_OF_ACCOUNT, no VIEW_TRUST); member N,
 * same role, NOT on the matter; member T on the matter with the system "member" role (VIEW_TRUST).
 * The rule: the matter check runs first (404), the trust check after it (403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedDocumentTrustAccessIntegrationTest {

  private static final String ORG_ID = "org_gen_doc_trust_access_test";
  private static final String OWNER = "user_gdta_owner";
  private static final String M = "user_gdta_m";
  private static final String N = "user_gdta_n";
  private static final String T = "user_gdta_t";
  private static final String TRUST_TITLE = "Trust details are not available";
  private static final String PERIOD =
      """
      {"periodStart":"2026-01-01","periodEnd":"2026-03-31"%s}""";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionService trustTransactionService;
  @Autowired private McpEnablementService mcpEnablementService;
  @Autowired private DocumentTools documentTools;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String schema;
  private UUID ownerId;
  private UUID mId;
  private UUID clientId;
  private UUID matterId;

  /** Statement of Account generated by the owner (trust printed). */
  private String soaId;

  private String soaDocumentId;

  /** Statement of Account generated by the owner and shared with the client (PORTAL). */
  private String sharedSoaDocumentId;

  /** Client Trust Statement generated by the owner, saved to the client's documents. */
  private String ctsId;

  private String ctsDocumentId;

  /** Power of Attorney for the same client (no trust variables in its body). */
  private String poaId;

  private String poaDocumentId;

  /** Statement of Account generated by M (trust withheld at generation). */
  private String soaByMId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Trust Access Attorneys", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "gdta_owner@test.com", "GDTA Owner", "owner"));
    mId =
        UUID.fromString(
            TestMemberHelper.syncMember(mockMvc, ORG_ID, M, "gdta_m@test.com", "GDTA M", "member"));
    UUID nId =
        UUID.fromString(
            TestMemberHelper.syncMember(mockMvc, ORG_ID, N, "gdta_n@test.com", "GDTA N", "member"));
    UUID tId =
        UUID.fromString(
            TestMemberHelper.syncMember(mockMvc, ORG_ID, T, "gdta_t@test.com", "GDTA T", "member"));

    String trustlessRole =
        createCustomRole("Trustless Clerk", "VIEW_LEGAL", "GENERATE_STATEMENT_OF_ACCOUNT");
    assignRole(mId, trustlessRole);
    assignRole(nId, trustlessRole);

    clientId =
        inTenantTx(
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Mokoena Holdings", "mokoena@test.com", ownerId))
                    .getId());
    var created =
        mockMvc
            .perform(
                post("/api/matters")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Mokoena transfer","clientId":"%s","workType":"CONVEYANCING"}"""
                            .formatted(clientId)))
            .andExpect(status().isCreated())
            .andReturn();
    matterId = UUID.fromString(TestEntityHelper.extractId(created));
    addToMatter(mId);
    addToMatter(tId);

    inTenantTx(
        () -> {
          UUID accountId =
              trustAccountService
                  .createTrustAccount(
                      new CreateTrustAccountRequest(
                          "Mokoena Trust Account",
                          "Standard Bank",
                          "051001",
                          "6200000009",
                          "GENERAL",
                          true,
                          false,
                          null,
                          LocalDate.of(2026, 1, 1),
                          "Trust access test"))
                  .id();
          return trustTransactionService.recordDeposit(
              accountId,
              new RecordDepositRequest(
                  clientId,
                  matterId,
                  new BigDecimal("4200.00"),
                  "DEP-GDTA-001",
                  "Transfer costs deposit",
                  LocalDate.of(2026, 2, 10)));
        });
    inTenantTx(
        () -> {
          mcpEnablementService.enable("popia-egress-v1");
          return portalContactService.createContact(
              ORG_ID,
              clientId,
              "mokoena@test.com",
              "Mokoena Contact",
              PortalContact.ContactRole.PRIMARY);
        });

    soaId = generateStatement(owner(), "");
    soaDocumentId = linkedDocumentId(TemplateEntityType.PROJECT, matterId, soaId);
    String sharedSoaId = generateStatement(owner(), ",\"shareWithClient\":true");
    sharedSoaDocumentId = linkedDocumentId(TemplateEntityType.PROJECT, matterId, sharedSoaId);
    soaByMId = generateStatement(memberJwt(M), "");

    String[] cts = generateForClient("client-trust-statement");
    ctsId = cts[0];
    ctsDocumentId = cts[1];
    String[] poa = generateForClient("power-of-attorney");
    poaId = poa[0];
    poaDocumentId = poa[1];
  }

  // ---- 1. SoA download -----------------------------------------------------------------

  @Test
  void ownerStatement_memberWithoutViewTrust_downloadForbidden() throws Exception {
    mockMvc
        .perform(get("/api/generated-documents/" + soaId + "/download").with(memberJwt(M)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value(TRUST_TITLE));
  }

  // ---- 2. the same SoA's linked document -------------------------------------------------

  @Test
  void ownerStatementLinkedDocument_memberWithoutViewTrust_presignForbidden() throws Exception {
    mockMvc
        .perform(get("/api/documents/" + soaDocumentId + "/presign-download").with(memberJwt(M)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value(TRUST_TITLE));
  }

  // ---- 3. Client Trust Statement --------------------------------------------------------

  @Test
  void clientTrustStatement_memberWithoutViewTrust_downloadAndPresignForbidden() throws Exception {
    mockMvc
        .perform(get("/api/generated-documents/" + ctsId + "/download").with(memberJwt(M)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value(TRUST_TITLE));
    mockMvc
        .perform(get("/api/documents/" + ctsDocumentId + "/presign-download").with(memberJwt(M)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value(TRUST_TITLE));
  }

  // ---- 4. no over-flagging: Power of Attorney for the same client -----------------------

  @Test
  void powerOfAttorney_memberWithoutViewTrust_downloadAllowed() throws Exception {
    mockMvc
        .perform(get("/api/generated-documents/" + poaId + "/download").with(memberJwt(M)))
        .andExpect(status().isFound());
    mockMvc
        .perform(get("/api/documents/" + poaDocumentId + "/presign-download").with(memberJwt(M)))
        .andExpect(status().isOk());
  }

  // ---- 5. a statement M generated (trust withheld) carries no trust data ---------------

  @Test
  void statementGeneratedByMemberWithoutViewTrust_downloadAllowed() throws Exception {
    mockMvc
        .perform(get("/api/generated-documents/" + soaByMId + "/download").with(memberJwt(M)))
        .andExpect(status().isFound());
  }

  // ---- 6. VIEW_TRUST member passes; the matter rule still comes first ------------------

  @Test
  void viewTrustMember_downloadAllowed() throws Exception {
    mockMvc
        .perform(get("/api/generated-documents/" + soaId + "/download").with(memberJwt(T)))
        .andExpect(status().isFound());
    mockMvc
        .perform(get("/api/documents/" + soaDocumentId + "/presign-download").with(memberJwt(T)))
        .andExpect(status().isOk());
  }

  @Test
  void memberNotOnMatter_stillNotFound_matterCheckRunsFirst() throws Exception {
    mockMvc
        .perform(get("/api/generated-documents/" + soaId + "/download").with(memberJwt(N)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/documents/" + soaDocumentId + "/presign-download").with(memberJwt(N)))
        .andExpect(status().isNotFound());
  }

  // ---- 7. MCP get_document_url -----------------------------------------------------------

  @Test
  void mcpGetDocumentUrl_memberWithoutViewTrust_forbiddenWithoutUrl() {
    Object result =
        ScopedValue.where(RequestScopes.TENANT_ID, schema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .where(RequestScopes.MEMBER_ID, mId)
            .where(RequestScopes.ORG_ROLE, "member")
            .where(
                RequestScopes.CAPABILITIES, Set.of("VIEW_LEGAL", "GENERATE_STATEMENT_OF_ACCOUNT"))
            .call(() -> documentTools.getDocumentUrl(UUID.fromString(soaDocumentId)));

    assertThat(result).isInstanceOf(CallToolResult.class);
    var toolResult = (CallToolResult) result;
    assertThat(toolResult.isError()).isTrue();
    String text = ((TextContent) toolResult.content().getFirst()).text();
    assertThat(text).contains("\"forbidden\"").doesNotContain("url").doesNotContain("http");
  }

  // ---- 8. portal: the client still gets its own shared statement --------------------------

  @Test
  void portalContact_sharedStatement_presignAllowed() throws Exception {
    String token = portalJwtService.issueToken(clientId, ORG_ID);
    mockMvc
        .perform(
            get("/portal/documents/{documentId}/presign-download", sharedSoaDocumentId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.presignedUrl").isNotEmpty());
  }

  // ---- lists keep the rows and carry the flag (D19) ---------------------------------------

  @Test
  void generatedDocumentLists_carryContainsTrustData() throws Exception {
    String matterList = listGenerated(TemplateEntityType.PROJECT, matterId);
    assertThat(flag(matterList, "$[?(@.id == '%s')].containsTrustData", soaId)).isTrue();
    assertThat(flag(matterList, "$[?(@.id == '%s')].containsTrustData", soaByMId)).isFalse();

    String clientList = listGenerated(TemplateEntityType.CUSTOMER, clientId);
    assertThat(flag(clientList, "$[?(@.id == '%s')].containsTrustData", ctsId)).isTrue();
    assertThat(flag(clientList, "$[?(@.id == '%s')].containsTrustData", poaId)).isFalse();
  }

  @Test
  void documentLists_carryContainsTrustData() throws Exception {
    String matterDocs =
        mockMvc
            .perform(get("/api/matters/" + matterId + "/documents").with(memberJwt(M)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(flag(matterDocs, "$[?(@.id == '%s')].containsTrustData", soaDocumentId)).isTrue();

    String clientDocs =
        mockMvc
            .perform(
                get("/api/documents")
                    .param("scope", "CUSTOMER")
                    .param("customerId", clientId.toString())
                    .with(memberJwt(M)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(flag(clientDocs, "$[?(@.id == '%s')].containsTrustData", ctsDocumentId)).isTrue();
    assertThat(flag(clientDocs, "$[?(@.id == '%s')].containsTrustData", poaDocumentId)).isFalse();
  }

  @Test
  void statementsList_carriesContainsTrustData() throws Exception {
    String page =
        mockMvc
            .perform(get("/api/matters/" + matterId + "/statements").with(memberJwt(M)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(flag(page, "$.content[?(@.id == '%s')].containsTrustData", soaId)).isTrue();
    assertThat(flag(page, "$.content[?(@.id == '%s')].containsTrustData", soaByMId)).isFalse();
  }

  // ---- helpers ------------------------------------------------------------------------------

  private static JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private static JwtRequestPostProcessor memberJwt(String subject) {
    return TestJwtFactory.memberJwt(ORG_ID, subject);
  }

  /** The single boolean a JsonPath filter selects; fails when the row or field is absent. */
  private static boolean flag(String json, String pathTemplate, String id) {
    List<Object> values = JsonPath.read(json, pathTemplate.formatted(id));
    assertThat(values).as("row %s carries the field", id).hasSize(1);
    assertThat(values.getFirst()).as("row %s field is a boolean", id).isInstanceOf(Boolean.class);
    return (Boolean) values.getFirst();
  }

  private String listGenerated(TemplateEntityType type, UUID entityId) throws Exception {
    return mockMvc
        .perform(
            get("/api/generated-documents")
                .param("entityType", type.name())
                .param("entityId", entityId.toString())
                .with(owner()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String linkedDocumentId(TemplateEntityType type, UUID entityId, String generatedId)
      throws Exception {
    List<Object> ids =
        JsonPath.read(
            listGenerated(type, entityId), "$[?(@.id == '%s')].documentId".formatted(generatedId));
    return ids.getFirst().toString();
  }

  private String generateStatement(JwtRequestPostProcessor jwt, String extraFields)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters/" + matterId + "/statements")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PERIOD.formatted(extraFields)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  /** Owner generates a pack template for the client, saved to documents: {generatedId, docId}. */
  private String[] generateForClient(String packTemplateKey) throws Exception {
    UUID templateId =
        inTenantTx(
            () ->
                documentTemplateRepository
                    .findPackSeeded("legal-za", packTemplateKey)
                    .getFirst()
                    .getId());
    String body =
        mockMvc
            .perform(
                post("/api/templates/" + templateId + "/generate")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityId":"%s","saveToDocuments":true,"acknowledgeWarnings":true}"""
                            .formatted(clientId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new String[] {
      JsonPath.read(body, "$.id").toString(), JsonPath.read(body, "$.documentId").toString()
    };
  }

  private String createCustomRole(String name, String... capabilities) throws Exception {
    String caps = objectMapper.writeValueAsString(List.of(capabilities));
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":%s}"""
                            .formatted(name, caps)))
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

  private void addToMatter(UUID memberId) throws Exception {
    mockMvc
        .perform(
            post("/api/matters/" + matterId + "/members")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":\"%s\"}".formatted(memberId)))
        .andExpect(status().isCreated());
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("MANAGE_TRUST", "VIEW_TRUST"))
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
