package io.b2mash.b2b.b2bstrawman.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentProvenanceTest {

  private static final String ORG_ID = "org_doc_provenance_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private AiExecutionRepository aiExecutionRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Doc Provenance Test Org", null);
    String ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_prov_owner", "prov_owner@test.com", "Prov Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(() -> transactionTemplate.executeWithoutResult(status -> action.run()));
  }

  @Test
  void newDocument_hasSourceManual_byDefault() {
    runInTenant(
        () -> {
          var doc =
              new Document(
                  Document.Scope.ORG,
                  null,
                  null,
                  "test.pdf",
                  "application/pdf",
                  1024L,
                  ownerMemberId,
                  Document.Visibility.INTERNAL);
          doc.assignS3Key("test/key");
          Document saved = documentRepository.save(doc);

          assertThat(saved.getSource()).isEqualTo(Document.Source.MANUAL);
          assertThat(saved.getAiExecutionId()).isNull();
        });
  }

  @Test
  void scopeAwareConstructor_hasSourceManual_byDefault() {
    runInTenant(
        () -> {
          var doc =
              new Document(
                  Document.Scope.ORG,
                  null,
                  null,
                  "org-doc.pdf",
                  "application/pdf",
                  2048L,
                  ownerMemberId,
                  Document.Visibility.INTERNAL);
          doc.assignS3Key("test/org-key");
          Document saved = documentRepository.save(doc);

          assertThat(saved.getSource()).isEqualTo(Document.Source.MANUAL);
          assertThat(saved.getAiExecutionId()).isNull();
        });
  }

  @Test
  void markAsAiGenerated_setsBothFields() {
    runInTenant(
        () -> {
          // Create an AiExecution so the FK is valid
          var execution =
              new AiExecution(
                  "test-skill", "DOCUMENT", UUID.randomUUID(), ownerMemberId, "claude-sonnet", 1);
          AiExecution savedExecution = aiExecutionRepository.save(execution);

          var doc =
              new Document(
                  Document.Scope.ORG,
                  null,
                  null,
                  "ai-doc.pdf",
                  "application/pdf",
                  512L,
                  ownerMemberId,
                  Document.Visibility.INTERNAL);
          doc.assignS3Key("test/ai-key");
          doc.markAsAiGenerated(savedExecution.getId());
          Document saved = documentRepository.save(doc);

          assertThat(saved.getSource()).isEqualTo(Document.Source.AI_GENERATED);
          assertThat(saved.getAiExecutionId()).isEqualTo(savedExecution.getId());
        });
  }

  @Test
  void aiExecutionId_fkConstraint_validatesAgainstAiExecutionsTable() {
    runInTenant(
        () -> {
          // Create a real execution to validate the FK works
          var execution =
              new AiExecution(
                  "test-skill", "DOCUMENT", UUID.randomUUID(), ownerMemberId, "claude-sonnet", 1);
          AiExecution savedExecution = aiExecutionRepository.save(execution);

          var doc =
              new Document(
                  Document.Scope.ORG,
                  null,
                  null,
                  "fk-doc.pdf",
                  "application/pdf",
                  256L,
                  ownerMemberId,
                  Document.Visibility.INTERNAL);
          doc.assignS3Key("test/fk-key");
          doc.markAsAiGenerated(savedExecution.getId());
          Document saved = documentRepository.save(doc);

          // Verify the FK relationship is persisted correctly
          Document reloaded = documentRepository.findById(saved.getId()).orElseThrow();
          assertThat(reloaded.getAiExecutionId()).isEqualTo(savedExecution.getId());
        });
  }
}
