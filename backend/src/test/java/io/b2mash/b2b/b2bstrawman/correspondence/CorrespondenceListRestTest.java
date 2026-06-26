package io.b2mash.b2b.b2bstrawman.correspondence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceCommand;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 586A — read-only correspondence list REST: newest-first ordering, {@code attachmentCount} +
 * {@code direction} in the row, and the page-size clamp at {@link
 * io.b2mash.b2b.b2bstrawman.mcp.McpPagination#DEFAULT_MAX_SIZE} (50). Access/isolation is in {@link
 * CorrespondenceListAccessTest}. Surefire ({@code -Dtest=...}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorrespondenceListRestTest {

  private static final String ORG_ID = "org_corr_586_rest";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceService correspondenceService;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID matterId;
  private UUID clientId;
  private JwtRequestPostProcessor owner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Corr 586 REST Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    matterId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "List Matter"));
    clientId =
        UUID.fromString(
            TestEntityHelper.createCustomer(mockMvc, owner, "List Client", "lc@test.com"));

    // Two correspondences with explicit receivedAt so ordering is deterministic.
    seed("<older@mail.test>", "Older subject", Instant.parse("2026-01-01T10:00:00Z"));
    seed("<newer@mail.test>", "Newer subject", Instant.parse("2026-02-01T10:00:00Z"));
  }

  private void seed(String messageId, String subject, Instant receivedAt) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx ->
                        correspondenceService.fileInbound(
                            new FileCorrespondenceCommand(
                                matterId,
                                clientId,
                                messageId,
                                subject,
                                "Body text",
                                null,
                                "jane@acme.co.za",
                                null,
                                null,
                                null,
                                receivedAt,
                                null,
                                "MCP"),
                            new ActorContext(ownerMemberId, "owner"))));
  }

  @Test
  void projectListReturnsNewestFirstWithAttachmentCountAndDirection() throws Exception {
    mockMvc
        .perform(get("/api/projects/{id}/correspondence", matterId).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].subject").value("Newer subject"))
        .andExpect(jsonPath("$.content[1].subject").value("Older subject"))
        .andExpect(jsonPath("$.content[0].attachmentCount").value(0))
        .andExpect(jsonPath("$.content[0].direction").value("INBOUND"))
        .andExpect(jsonPath("$.content[0].fromAddress").value("jane@acme.co.za"));
  }

  @Test
  void customerListReturnsNewestFirst() throws Exception {
    mockMvc
        .perform(get("/api/customers/{id}/correspondence", clientId).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].subject").value("Newer subject"))
        .andExpect(jsonPath("$.content[1].subject").value("Older subject"));
  }

  @Test
  void pageSizeIsClampedToFifty() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/{id}/correspondence", matterId).param("size", "500").with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(50));
  }
}
