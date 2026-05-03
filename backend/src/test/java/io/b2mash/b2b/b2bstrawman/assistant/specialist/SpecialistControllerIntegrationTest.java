package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billing.PlanTier;
import io.b2mash.b2b.b2bstrawman.billing.PlanTierResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 70 / Epic 511B integration tests for the specialist HTTP endpoints. Covers visibility on
 * the listing endpoint, the PRO-tier 403 on session start, the unknown-id 404, and the
 * capability-narrowed tool subset returned by start-session. {@link PlanTierResolver} is mocked so
 * tests can flip between PRO and STARTER without touching the subscription tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpecialistControllerIntegrationTest {

  private static final String ORG_ID = "org_specialist_ctrl_test";

  @MockitoBean private PlanTierResolver planTierResolver;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  @SuppressWarnings("unused")
  private UUID memberIdOwner;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Specialist Ctrl Test Org", null);
    var memberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_spec_owner", "spec_owner@test.com", "Spec Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdStr);
    orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @BeforeEach
  void resetTier() {
    when(planTierResolver.resolveForCurrentOrg()).thenReturn(PlanTier.PRO);
    when(planTierResolver.resolveForOrganization(org.mockito.ArgumentMatchers.any()))
        .thenReturn(PlanTier.PRO);
  }

  @Test
  void listSpecialistsReturnsAllThreeForProOwner() throws Exception {
    mockMvc
        .perform(
            get("/api/assistant/specialists")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[*].id", Matchers.containsInAnyOrder("BILLING", "INTAKE", "INBOX")));
  }

  @Test
  void listSpecialistsFiltersBySurface() throws Exception {
    mockMvc
        .perform(
            get("/api/assistant/specialists")
                .param("surface", "INVOICE_DRAFT_TOOLBAR")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("BILLING"));
  }

  @Test
  void listSpecialistsReturnsEmptyForStarterTier() throws Exception {
    when(planTierResolver.resolveForCurrentOrg()).thenReturn(PlanTier.STARTER);
    mockMvc
        .perform(
            get("/api/assistant/specialists")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void startSessionReturnsResolvedToolSubsetAndPromptHash() throws Exception {
    mockMvc
        .perform(
            post("/api/assistant/specialists/BILLING/sessions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contextRef\":null,\"initialPrompt\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.specialistId").value("BILLING"))
        .andExpect(jsonPath("$.systemPromptHash").value(Matchers.startsWith("sha256:")))
        .andExpect(jsonPath("$.sessionId").exists())
        .andExpect(jsonPath("$.resolvedToolIds").isArray());
  }

  @Test
  void startSessionReturns403ForStarterTier() throws Exception {
    when(planTierResolver.resolveForCurrentOrg()).thenReturn(PlanTier.STARTER);
    mockMvc
        .perform(
            post("/api/assistant/specialists/BILLING/sessions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void startSessionReturns404ForUnknownSpecialist() throws Exception {
    mockMvc
        .perform(
            post("/api/assistant/specialists/UNKNOWN_SPECIALIST/sessions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }
}
