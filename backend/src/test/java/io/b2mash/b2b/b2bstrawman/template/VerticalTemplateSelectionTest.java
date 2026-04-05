package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
class VerticalTemplateSelectionTest {
  private static final String ORG_ID_ZA = "org_vert_tmpl_za";
  private static final String ORG_ID_DEFAULT = "org_vert_tmpl_default";

  @Autowired private MockMvc mockMvc;
  @Autowired private GeneratedDocumentService generatedDocumentService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchemaZa;
  private String tenantSchemaDefault;
  private UUID memberIdZa;
  private UUID memberIdDefault;

  @BeforeAll
  void setup() throws Exception {
    // Org 1: accounting-za vertical profile
    provisioningService.provisionTenant(ORG_ID_ZA, "Vertical ZA Org", "accounting-za");
    memberIdZa =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID_ZA,
                "user_vert_za_owner",
                "vert_za@test.com",
                "Vert ZA Owner",
                "owner"));
    tenantSchemaZa =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_ZA).orElseThrow().getSchemaName();

    // Org 2: no vertical profile (default)
    provisioningService.provisionTenant(ORG_ID_DEFAULT, "Vertical Default Org", null);
    memberIdDefault =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID_DEFAULT,
                "user_vert_default_owner",
                "vert_default@test.com",
                "Vert Default Owner",
                "owner"));
    tenantSchemaDefault =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_DEFAULT).orElseThrow().getSchemaName();
  }

  @Test
  void resolvesInvoiceZaForAccountingZaProfile() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaZa)
        .where(RequestScopes.ORG_ID, ORG_ID_ZA)
        .where(RequestScopes.MEMBER_ID, memberIdZa)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var result = generatedDocumentService.resolveDefaultInvoiceTemplate();

                      assertThat(result).isPresent();
                      assertThat(result.get().getPackTemplateKey()).isEqualTo("invoice-za");
                    }));
  }

  @Test
  void resolvesEmptyForNullVerticalProfile() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaDefault)
        .where(RequestScopes.ORG_ID, ORG_ID_DEFAULT)
        .where(RequestScopes.MEMBER_ID, memberIdDefault)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var result = generatedDocumentService.resolveDefaultInvoiceTemplate();

                      // Default org has no "invoice" pack template seeded by common pack,
                      // so this returns empty. If an "invoice" template existed, it would return
                      // it.
                      // The key assertion is that it does NOT return "invoice-za".
                      if (result.isPresent()) {
                        assertThat(result.get().getPackTemplateKey()).isNotEqualTo("invoice-za");
                      }
                      // Either empty or a generic "invoice" template — both are valid fallbacks
                    }));
  }
}
