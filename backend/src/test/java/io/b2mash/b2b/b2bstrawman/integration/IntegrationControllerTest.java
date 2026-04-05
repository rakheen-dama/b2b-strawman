package io.b2mash.b2b.b2bstrawman.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationControllerTest {

  private static final String INTERNAL_API_KEY = "test-api-key";
  private static final String ORG_ID = "org_integration_ctrl_test";
  private static final String TEST_API_KEY = "sk_live_abc123def456xyz789";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String tenantSchema;

  @BeforeAll
  void provisionTenant() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Integration Controller Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_intctrl_owner", "intctrl_owner@test.com", "Owner", "owner");
    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_intctrl_admin", "intctrl_admin@test.com", "Admin", "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_intctrl_member", "intctrl_member@test.com", "Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void listIntegrations_returnsAllFiveDomains() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations").with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(5)))
        .andExpect(jsonPath("$[0].domain").value("ACCOUNTING"))
        .andExpect(jsonPath("$[0].providerSlug").value(nullValue()))
        .andExpect(jsonPath("$[0].enabled").value(false))
        .andExpect(jsonPath("$[1].domain").value("AI"))
        .andExpect(jsonPath("$[2].domain").value("DOCUMENT_SIGNING"))
        .andExpect(jsonPath("$[3].domain").value("EMAIL"))
        .andExpect(jsonPath("$[4].domain").value("PAYMENT"));
  }

  @Test
  @Order(2)
  void listProviders_returnsNoopForAllDomains() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/providers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ACCOUNTING").isArray())
        .andExpect(jsonPath("$.AI").isArray())
        .andExpect(jsonPath("$.DOCUMENT_SIGNING").isArray())
        .andExpect(jsonPath("$.PAYMENT").isArray());
  }

  @Test
  @Order(3)
  void memberCannotAccessIntegrationsApi() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations").with(TestJwtFactory.memberJwt(ORG_ID, "user_intctrl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(4)
  void adminCanAccessIntegrationsApi() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations").with(TestJwtFactory.adminJwt(ORG_ID, "user_intctrl_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(5)));
  }

  @Test
  @Order(10)
  void putUpsertsIntegrationConfig() throws Exception {
    mockMvc
        .perform(
            put("/api/integrations/ACCOUNTING")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"providerSlug": "noop", "configJson": "{\\"region\\": \\"za\\"}"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.domain").value("ACCOUNTING"))
        .andExpect(jsonPath("$.providerSlug").value("noop"))
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  @Order(20)
  void postSetKeyReturns204() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/ACCOUNTING/set-key")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"apiKey": "%s"}
                    """
                        .formatted(TEST_API_KEY)))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(21)
  void apiKeyNeverReturnedInListResponse() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/integrations")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner")))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    // keySuffix should be last 6 chars of TEST_API_KEY
    String expectedSuffix = TEST_API_KEY.substring(TEST_API_KEY.length() - 6);
    assertThat(body).contains(expectedSuffix);
    assertThat(body).doesNotContain(TEST_API_KEY);
  }

  @Test
  @Order(25)
  void setKeyBlankReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/ACCOUNTING/set-key")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"apiKey": ""}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(30)
  void postTestConnectionReturnsSuccess() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/ACCOUNTING/test")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.providerName").value("noop"));
  }

  @Test
  @Order(40)
  void patchToggleEnablesIntegration() throws Exception {
    mockMvc
        .perform(
            patch("/api/integrations/ACCOUNTING/toggle")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabled": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.domain").value("ACCOUNTING"))
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  @Order(50)
  void deleteKeyRemovesKeySuffix() throws Exception {
    // Delete the key
    mockMvc
        .perform(
            delete("/api/integrations/ACCOUNTING/key")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner")))
        .andExpect(status().isNoContent());

    // Verify keySuffix is now null
    mockMvc
        .perform(
            get("/api/integrations").with(TestJwtFactory.ownerJwt(ORG_ID, "user_intctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].domain").value("ACCOUNTING"))
        .andExpect(jsonPath("$[0].keySuffix").value(nullValue()));
  }

  @Test
  @Order(60)
  void memberCannotToggleIntegration() throws Exception {
    mockMvc
        .perform(
            patch("/api/integrations/ACCOUNTING/toggle")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_intctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabled": true}
                    """))
        .andExpect(status().isForbidden());
  }

  // --- Audit event assertions ---

  @Test
  @Order(100)
  void auditEventCreatedOnIntegrationConfigured() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var page =
                      auditEventRepository.findByFilter(
                          "org_integration",
                          null,
                          null,
                          "integration.configured",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(page.getContent()).isNotEmpty();
                  var event = page.getContent().getFirst();
                  assertThat(event.getEventType()).isEqualTo("integration.configured");
                  assertThat(event.getEntityType()).isEqualTo("org_integration");
                  assertThat(event.getDetails()).containsKey("domain");
                  assertThat(event.getDetails()).containsKey("providerSlug");
                }));
  }

  @Test
  @Order(101)
  void auditEventCreatedOnKeySetAndKeyRemoved() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Verify key_set event exists
                  var keySetPage =
                      auditEventRepository.findByFilter(
                          "org_integration",
                          null,
                          null,
                          "integration.key_set",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(keySetPage.getContent()).isNotEmpty();
                  var keySetEvent = keySetPage.getContent().getFirst();
                  assertThat(keySetEvent.getEventType()).isEqualTo("integration.key_set");
                  assertThat(keySetEvent.getEntityType()).isEqualTo("org_integration");
                  assertThat(keySetEvent.getDetails()).containsKey("domain");
                  // CRITICAL: API key must NEVER appear in audit details
                  assertThat(keySetEvent.getDetails()).doesNotContainKey("apiKey");
                  keySetEvent
                      .getDetails()
                      .values()
                      .forEach(
                          value ->
                              assertThat(String.valueOf(value))
                                  .as("Audit details must not contain the full API key")
                                  .doesNotContain(TEST_API_KEY));

                  // Verify key_removed event exists
                  var keyRemovedPage =
                      auditEventRepository.findByFilter(
                          "org_integration",
                          null,
                          null,
                          "integration.key_removed",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(keyRemovedPage.getContent()).isNotEmpty();
                  var keyRemovedEvent = keyRemovedPage.getContent().getFirst();
                  assertThat(keyRemovedEvent.getEventType()).isEqualTo("integration.key_removed");
                  assertThat(keyRemovedEvent.getEntityType()).isEqualTo("org_integration");
                  assertThat(keyRemovedEvent.getDetails()).containsKey("domain");
                }));
  }

  @Test
  @Order(102)
  void auditEventCreatedOnToggle() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var page =
                      auditEventRepository.findByFilter(
                          "org_integration",
                          null,
                          null,
                          "integration.enabled",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(page.getContent()).isNotEmpty();
                  var event = page.getContent().getFirst();
                  assertThat(event.getEventType()).isEqualTo("integration.enabled");
                  assertThat(event.getEntityType()).isEqualTo("org_integration");
                  assertThat(event.getDetails()).containsKey("domain");
                  assertThat(event.getDetails()).containsKey("providerSlug");
                }));
  }

  @Test
  @Order(103)
  void auditEventCreatedOnConnectionTested() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var page =
                      auditEventRepository.findByFilter(
                          "org_integration",
                          null,
                          null,
                          "integration.connection_tested",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(page.getContent()).isNotEmpty();
                  var event = page.getContent().getFirst();
                  assertThat(event.getEventType()).isEqualTo("integration.connection_tested");
                  assertThat(event.getEntityType()).isEqualTo("org_integration");
                  assertThat(event.getDetails()).containsKey("domain");
                  assertThat(event.getDetails()).containsKey("providerSlug");
                  assertThat(event.getDetails()).containsKey("success");
                }));
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
