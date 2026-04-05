package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
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
class AccountingTemplatePackSeederTest {
  private static final String ORG_ID = "org_atps_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(
        ORG_ID, "Accounting Template Pack Test Org", "accounting-za");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_atps_owner", "atps_owner@test.com", "ATPS Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void seedCreatesSevenAccountingTemplates() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var accountingTemplates =
                      templates.stream()
                          .filter(t -> "accounting-za".equals(t.getPackId()))
                          .toList();
                  assertThat(accountingTemplates).hasSize(7);
                }));
  }

  @SuppressWarnings("unchecked")
  @Test
  void seededContentIsValidTiptapJson() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var accountingTemplates =
                      templates.stream()
                          .filter(t -> "accounting-za".equals(t.getPackId()))
                          .toList();
                  assertThat(accountingTemplates).isNotEmpty();
                  assertThat(accountingTemplates)
                      .allSatisfy(
                          t -> {
                            assertThat(t.getContent()).isNotNull();
                            assertThat(t.getContent()).containsEntry("type", "doc");
                            assertThat(t.getContent()).containsKey("content");
                            assertThat(t.getContent().get("content")).isInstanceOf(List.class);
                            List<Map<String, Object>> content =
                                (List<Map<String, Object>>) t.getContent().get("content");
                            assertThat(content).isNotEmpty();
                          });
                }));
  }

  @Test
  void verticalProfileFieldIsTolerated() {
    // The pack.json contains "verticalProfile": "accounting-za" which is not on the DTO.
    // If this test runs without error, deserialization tolerates the unknown field.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var accountingTemplates =
                      templates.stream()
                          .filter(t -> "accounting-za".equals(t.getPackId()))
                          .toList();
                  assertThat(accountingTemplates)
                      .as("verticalProfile field should not break deserialization")
                      .hasSize(7);
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
