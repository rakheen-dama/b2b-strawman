package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplateRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.ResponseType;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the {@code conveyancing-intake-za} request pack (Epic 492B). Asserts the
 * 7-item conveyancing intake questionnaire seeds for a {@code legal-za} tenant via {@code
 * RequestPackSeeder}'s classpath scan and that vertical-profile gating prevents {@code
 * accounting-za} and {@code consulting-za} tenants from receiving it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConveyancingRequestPackTest {

  private static final String LEGAL_ORG_ID = "org_cvrp_legal_test";
  private static final String ACCOUNTING_ORG_ID = "org_cvrp_acct_test";
  private static final String CONSULTING_ORG_ID = "org_cvrp_cons_test";

  private static final String PACK_ID = "conveyancing-intake-za";

  private static final List<String> EXPECTED_ITEM_NAMES =
      List.of(
          "Party Identity & Contact Details",
          "Property Address, Erf & Deeds Office",
          "Purchase Price, Bond Amount & Bond Institution",
          "Occupation & Possession Dates",
          "FICA Documentation",
          "Marital Status (ANC / In Community of Property)",
          "Rates & Levy Account Contacts");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private RequestTemplateRepository requestTemplateRepository;
  @Autowired private RequestTemplateItemRepository requestTemplateItemRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String legalTenantSchema;
  private String accountingTenantSchema;
  private String consultingTenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(
        LEGAL_ORG_ID, "Conveyancing Request Pack Legal Test Org", "legal-za");
    legalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(
        ACCOUNTING_ORG_ID, "Conveyancing Request Pack Accounting Test Org", "accounting-za");
    accountingTenantSchema =
        orgSchemaMappingRepository
            .findByClerkOrgId(ACCOUNTING_ORG_ID)
            .orElseThrow()
            .getSchemaName();

    provisioningService.provisionTenant(
        CONSULTING_ORG_ID, "Conveyancing Request Pack Consulting Test Org", "consulting-za");
    consultingTenantSchema =
        orgSchemaMappingRepository
            .findByClerkOrgId(CONSULTING_ORG_ID)
            .orElseThrow()
            .getSchemaName();
  }

  @Test
  void legalTenantGetsOneConveyancingIntakeTemplateWithSevenItems() {
    runInTenant(
        legalTenantSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = requestTemplateRepository.findByPackId(PACK_ID);
                  assertThat(templates).hasSize(1);
                  var template = templates.getFirst();
                  assertThat(template.getName()).isEqualTo("Conveyancing Intake (SA)");

                  var items =
                      requestTemplateItemRepository.findByTemplateIdOrderBySortOrder(
                          template.getId());
                  assertThat(items).hasSize(7);
                  assertThat(items.stream().map(i -> i.getName()).toList())
                      .containsExactlyElementsOf(EXPECTED_ITEM_NAMES);

                  // FICA upload item is the sole FILE_UPLOAD; remaining six are TEXT_RESPONSE.
                  long fileUploads =
                      items.stream()
                          .filter(i -> i.getResponseType() == ResponseType.FILE_UPLOAD)
                          .count();
                  long textResponses =
                      items.stream()
                          .filter(i -> i.getResponseType() == ResponseType.TEXT_RESPONSE)
                          .count();
                  assertThat(fileUploads).isEqualTo(1);
                  assertThat(textResponses).isEqualTo(6);

                  var ficaItem =
                      items.stream()
                          .filter(i -> "FICA Documentation".equals(i.getName()))
                          .findFirst()
                          .orElseThrow();
                  assertThat(ficaItem.getResponseType()).isEqualTo(ResponseType.FILE_UPLOAD);
                  assertThat(ficaItem.getFileTypeHints()).contains("PDF");
                }));
  }

  @Test
  void nonLegalTenantsDoNotReceiveConveyancingIntakeRequestPack() {
    runInTenant(
        accountingTenantSchema,
        ACCOUNTING_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    assertThat(requestTemplateRepository.findByPackId(PACK_ID))
                        .as("accounting-za tenant must not receive conveyancing-intake-za pack")
                        .isEmpty()));

    runInTenant(
        consultingTenantSchema,
        CONSULTING_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    assertThat(requestTemplateRepository.findByPackId(PACK_ID))
                        .as("consulting-za tenant must not receive conveyancing-intake-za pack")
                        .isEmpty()));
  }

  private void runInTenant(String schema, String orgId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
