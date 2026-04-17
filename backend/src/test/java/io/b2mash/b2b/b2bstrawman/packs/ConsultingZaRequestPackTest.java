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
 * Integration tests for the {@code consulting-za-creative-brief} request pack: asserts the
 * 10-question creative brief seeds for a {@code consulting-za} tenant via {@code
 * RequestPackSeeder}'s classpath scan and that the ordered item shape (names, response types,
 * required flags) matches the content policy.
 *
 * <p>No customer-creation auto-assignment listener exists in the codebase; this test asserts only
 * seeding and shape, mirroring the analogous {@code YearEndRequestPackTest} pattern.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultingZaRequestPackTest {

  private static final String ORG_ID = "org_czrp_test";

  private static final String PACK_ID = "consulting-za-creative-brief";

  private static final List<String> EXPECTED_ITEM_NAMES =
      List.of(
          "Brand & Company Description",
          "Target Audience",
          "Core Business Goals",
          "Competitive Landscape & Reference Brands",
          "Must-Have Deliverables",
          "Known Constraints or Brand Guidelines",
          "Existing Assets or Content",
          "Tone of Voice Preferences",
          "Key Stakeholders & Decision-Making Process",
          "Launch & Milestone Dates");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private RequestTemplateRepository requestTemplateRepository;
  @Autowired private RequestTemplateItemRepository requestTemplateItemRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(
        ORG_ID, "Consulting ZA Request Pack Test Org", "consulting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void consultingTenantGetsOneTemplateWith10Items() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = requestTemplateRepository.findByPackId(PACK_ID);
                  assertThat(templates).hasSize(1);
                  var template = templates.getFirst();
                  assertThat(template.getName()).isEqualTo("Creative Brief");
                  var items =
                      requestTemplateItemRepository.findByTemplateIdOrderBySortOrder(
                          template.getId());
                  assertThat(items).hasSize(10);
                  assertThat(items.stream().map(i -> i.getName()).toList())
                      .containsExactlyElementsOf(EXPECTED_ITEM_NAMES);
                }));
  }

  @Test
  void responseTypeDistributionIsTwoFileUploadsAndEightTextResponses() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template = requestTemplateRepository.findByPackId(PACK_ID).getFirst();
                  var items =
                      requestTemplateItemRepository.findByTemplateIdOrderBySortOrder(
                          template.getId());

                  // Items 6 (Known Constraints) and 7 (Existing Assets) are FILE_UPLOAD.
                  assertThat(items.get(5).getResponseType()).isEqualTo(ResponseType.FILE_UPLOAD);
                  assertThat(items.get(6).getResponseType()).isEqualTo(ResponseType.FILE_UPLOAD);

                  // Remaining 8 items are TEXT_RESPONSE.
                  assertThat(items.get(0).getResponseType()).isEqualTo(ResponseType.TEXT_RESPONSE);
                  assertThat(items.get(1).getResponseType()).isEqualTo(ResponseType.TEXT_RESPONSE);
                  assertThat(items.get(2).getResponseType()).isEqualTo(ResponseType.TEXT_RESPONSE);
                  assertThat(items.get(3).getResponseType()).isEqualTo(ResponseType.TEXT_RESPONSE);
                  assertThat(items.get(4).getResponseType()).isEqualTo(ResponseType.TEXT_RESPONSE);
                  assertThat(items.get(7).getResponseType()).isEqualTo(ResponseType.TEXT_RESPONSE);
                  assertThat(items.get(8).getResponseType()).isEqualTo(ResponseType.TEXT_RESPONSE);
                  assertThat(items.get(9).getResponseType()).isEqualTo(ResponseType.TEXT_RESPONSE);

                  long fileUploads =
                      items.stream()
                          .filter(i -> i.getResponseType() == ResponseType.FILE_UPLOAD)
                          .count();
                  long textResponses =
                      items.stream()
                          .filter(i -> i.getResponseType() == ResponseType.TEXT_RESPONSE)
                          .count();
                  assertThat(fileUploads).isEqualTo(2);
                  assertThat(textResponses).isEqualTo(8);
                }));
  }

  @Test
  void requiredFlagsFollowContentPolicy() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template = requestTemplateRepository.findByPackId(PACK_ID).getFirst();
                  var items =
                      requestTemplateItemRepository.findByTemplateIdOrderBySortOrder(
                          template.getId());

                  // Items 1, 2, 3 (brand, audience, goals) are required — core brief inputs.
                  assertThat(items.get(0).isRequired()).isTrue(); // Brand & Company Description
                  assertThat(items.get(1).isRequired()).isTrue(); // Target Audience
                  assertThat(items.get(2).isRequired()).isTrue(); // Core Business Goals

                  // Items 4-10 are not required — nice-to-have context.
                  assertThat(items.get(3).isRequired()).isFalse(); // Competitive Landscape
                  assertThat(items.get(4).isRequired()).isFalse(); // Must-Have Deliverables
                  assertThat(items.get(5).isRequired()).isFalse(); // Known Constraints
                  assertThat(items.get(6).isRequired()).isFalse(); // Existing Assets
                  assertThat(items.get(7).isRequired()).isFalse(); // Tone of Voice
                  assertThat(items.get(8).isRequired()).isFalse(); // Key Stakeholders
                  assertThat(items.get(9).isRequired()).isFalse(); // Launch Dates
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
