package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.correspondence.CorrespondenceService;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.CorrespondenceListResponse;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceCommand;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 586C — Phase 81 BYOC capstone. Proves the two hard boundaries the gated-MCP-write-back chapter
 * promised:
 *
 * <ol>
 *   <li><b>Tenant isolation regression</b> — correspondence filed under tenant A is invisible to
 *       tenant B's read path (the {@link CorrespondenceService} list endpoints the new tab
 *       consumes). Enforced solely by schema-per-tenant {@code search_path}; no phase-specific
 *       isolation code.
 *   <li><b>BYOC source boundary</b> — Phase 81 added NO Gmail/Google/IMAP ingestion dependency, NO
 *       inbound webhook / scheduled mailbox poll, and NO Anthropic/LLM call in the Kazi backend.
 *       Correspondence arrives only via the firm's own Claude calling the {@code
 *       file_correspondence} MCP tool. These are asserted by reading {@code pom.xml} and the {@code
 *       correspondence/} + Phase-81 MCP-tool source, so they run under {@code ./mvnw verify}.
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase81BoundaryTest {

  private static final String ORG_A = "org_phase81_boundary_a";
  private static final String ORG_B = "org_phase81_boundary_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceService correspondenceService;

  private Tenant tenantA;
  private Tenant tenantB;

  private record Tenant(String schema, UUID ownerMemberId, UUID matterId) {}

  @BeforeAll
  void setup() throws Exception {
    tenantA = provision(ORG_A, "Phase81 Boundary A", "user_p81_a");
    tenantB = provision(ORG_B, "Phase81 Boundary B", "user_p81_b");

    // File a correspondence into tenant A's matter only.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantA.schema())
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx ->
                        correspondenceService.fileInbound(
                            new FileCorrespondenceCommand(
                                tenantA.matterId(),
                                null,
                                "<p81-tenant-a@mail.test>",
                                "Tenant A only",
                                "Body",
                                null,
                                "from@a.test",
                                null,
                                null,
                                null,
                                null,
                                null,
                                "MCP"),
                            new ActorContext(tenantA.ownerMemberId(), "owner"))));
  }

  private Tenant provision(String orgId, String orgName, String userSubject) throws Exception {
    provisioningService.provisionTenant(orgId, orgName, null);
    UUID owner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, orgId, userSubject, userSubject + "@test.com", "Owner", "owner"));
    String schema =
        orgSchemaMappingRepository.findByClerkOrgId(orgId).orElseThrow().getSchemaName();
    UUID matter =
        UUID.fromString(
            TestEntityHelper.createProject(
                mockMvc, TestJwtFactory.ownerJwt(orgId, userSubject), orgName + " Matter"));
    return new Tenant(schema, owner, matter);
  }

  // ── 1. Tenant-isolation regression ──────────────────────────────────────────

  @Test
  void tenantBCannotReadTenantAFiledCorrespondence() {
    // Read tenant A's matter list while bound to tenant B's schema: search_path makes A's row
    // invisible, so the page is empty (and A's matter id resolves to nothing in B).
    List<CorrespondenceListResponse> seenInB =
        listByProjectAs(tenantB.schema(), tenantA.matterId());
    assertThat(seenInB).isEmpty();

    // Sanity: the row IS visible to tenant A.
    List<CorrespondenceListResponse> seenInA =
        listByProjectAs(tenantA.schema(), tenantA.matterId());
    assertThat(seenInA).extracting(CorrespondenceListResponse::subject).contains("Tenant A only");
  }

  private List<CorrespondenceListResponse> listByProjectAs(String schema, UUID projectId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx ->
                        correspondenceService
                            .listByProject(projectId, PageRequest.of(0, 50))
                            .getContent()));
  }

  // ── 2. BYOC source boundary (no mail ingestion / webhook / LLM added by Phase 81) ────────────

  @Test
  void noGmailGoogleOrImapDependencyAdded() throws IOException {
    String pom = Files.readString(projectRoot().resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat(pom)
        .as("Phase 81 is BYOC — the firm's own Claude pushes mail in; Kazi pulls nothing")
        .doesNotContain("com.google")
        .doesNotContain("google-api")
        .doesNotContain("gmail")
        .doesNotContain("jakarta.mail")
        .doesNotContain("javax.mail")
        .doesNotContain("angus-mail")
        .doesNotContain("greenmail-imap");
  }

  @Test
  void noInboundWebhookOrScheduledMailboxPollAddedInCorrespondence() throws IOException {
    for (String src : correspondenceAndPhase81ToolSources()) {
      assertThat(src)
          .as("Phase 81 adds no inbound webhook / scheduled mailbox poll — ingestion is MCP-only")
          .doesNotContain("@Scheduled")
          .doesNotContain("@PostMapping")
          .doesNotContain("@PutMapping")
          .doesNotContain("ImapStore")
          .doesNotContain("Folder.open");
    }
  }

  @Test
  void noAnthropicOrLlmCallAddedInCorrespondenceOrPhase81Tools() throws IOException {
    for (String src : correspondenceAndPhase81ToolSources()) {
      assertThat(src)
          .as("Phase 81 adds no Anthropic/LLM call to the Kazi backend — the LLM is the client")
          .doesNotContain("anthropic")
          .doesNotContain("AiProvider")
          .doesNotContain("chatCompletion")
          .doesNotContain("ChatClient")
          .doesNotContain(".generate(");
    }
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private static Path projectRoot() {
    // Test working dir is the backend module root (where pom.xml + src/ live).
    return Path.of("").toAbsolutePath();
  }

  /** Reads every Java source under correspondence/ plus the Phase-81 MCP write tool. */
  private static List<String> correspondenceAndPhase81ToolSources() throws IOException {
    Path root = projectRoot();
    Path correspondenceDir = root.resolve("src/main/java/io/b2mash/b2b/b2bstrawman/correspondence");
    Path phase81Tool =
        root.resolve(
            "src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CorrespondenceWriteTools.java");

    try (Stream<Path> walk = Files.walk(correspondenceDir)) {
      List<Path> files =
          Stream.concat(walk.filter(p -> p.toString().endsWith(".java")), Stream.of(phase81Tool))
              .toList();
      assertThat(files).as("correspondence sources must exist").isNotEmpty();
      return files.stream().map(Phase81BoundaryTest::readQuietly).toList();
    }
  }

  private static String readQuietly(Path p) {
    try {
      return Files.readString(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read " + p, e);
    }
  }
}
