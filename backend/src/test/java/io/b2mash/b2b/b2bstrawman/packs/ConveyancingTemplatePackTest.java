package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import java.util.List;
import java.util.Set;
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
 * Integration tests for the {@code legal-za} document template pack with the conveyancing
 * extensions (Epic 492B). Asserts that the four new conveyancing Tiptap templates
 * (offer-to-purchase, deed-of-transfer, power-of-attorney-transfer, bond-cancellation-instruction)
 * install alongside the pre-existing legal-za templates for a {@code legal-za} tenant, and that the
 * {@code acceptanceEligible} flag (V101 column, wired by 489A's {@code TemplatePackSeeder})
 * round-trips from the manifest entry through to {@link DocumentTemplate#isAcceptanceEligible()}.
 *
 * <p>Test #5 asserts the same ordered-list query the frontend consumes ({@code
 * findByActiveTrueOrderBySortOrder()}) surfaces the two acceptance-eligible conveyancing templates
 * as the Phase 28 AcceptanceRequest template-picker seam.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConveyancingTemplatePackTest {

  private static final String ORG_ID = "org_cvtp_legal_test";

  private static final String PACK_ID = "legal-za";

  private static final int EXPECTED_LEGAL_ZA_TEMPLATE_COUNT = 16;

  private static final Set<String> PRE_PHASE_TEMPLATE_KEYS =
      Set.of(
          "engagement-letter-litigation",
          "engagement-letter-conveyancing",
          "engagement-letter-general",
          "power-of-attorney",
          "notice-of-motion",
          "founding-affidavit",
          "letter-of-demand",
          "client-trust-statement",
          "trust-receipt",
          "section-35-cover-letter");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Conveyancing Template Pack Test Org", "legal-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void legalTenantGets16TemplatesIncludingFourConveyancingOnes() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var legalTemplates =
                      documentTemplateRepository.findByActiveTrueOrderBySortOrder().stream()
                          .filter(t -> PACK_ID.equals(t.getPackId()))
                          .toList();

                  assertThat(legalTemplates).hasSize(EXPECTED_LEGAL_ZA_TEMPLATE_COUNT);

                  var keys =
                      legalTemplates.stream().map(DocumentTemplate::getPackTemplateKey).toList();
                  assertThat(keys)
                      .contains(
                          "offer-to-purchase",
                          "deed-of-transfer",
                          "power-of-attorney-transfer",
                          "bond-cancellation-instruction");
                }));
  }

  @Test
  void offerToPurchaseAndPowerOfAttorneyTransferAreAcceptanceEligible() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var otp =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(PACK_ID, "offer-to-purchase")
                          .orElseThrow();
                  assertThat(otp.isAcceptanceEligible()).isTrue();

                  var poaTransfer =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(PACK_ID, "power-of-attorney-transfer")
                          .orElseThrow();
                  assertThat(poaTransfer.isAcceptanceEligible()).isTrue();
                }));
  }

  @Test
  void deedOfTransferAndBondCancellationAreNotAcceptanceEligible() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var deed =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(PACK_ID, "deed-of-transfer")
                          .orElseThrow();
                  assertThat(deed.isAcceptanceEligible()).isFalse();

                  var bondCancel =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(PACK_ID, "bond-cancellation-instruction")
                          .orElseThrow();
                  assertThat(bondCancel.isAcceptanceEligible()).isFalse();
                }));
  }

  @Test
  void prePhaseLegalTemplatesDefaultToNonAcceptanceEligible() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var prePhaseTemplates =
                      documentTemplateRepository.findByActiveTrueOrderBySortOrder().stream()
                          .filter(t -> PACK_ID.equals(t.getPackId()))
                          .filter(t -> PRE_PHASE_TEMPLATE_KEYS.contains(t.getPackTemplateKey()))
                          .toList();

                  assertThat(prePhaseTemplates).hasSize(PRE_PHASE_TEMPLATE_KEYS.size());
                  assertThat(prePhaseTemplates)
                      .allSatisfy(t -> assertThat(t.isAcceptanceEligible()).isFalse());
                }));
  }

  @Test
  void acceptanceEligibleListQuerySurfacesOtpAndPoaButNotDeedOfTransfer() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Same ordered-list query the frontend consumes via
                  // DocumentTemplateService.listTemplates(). Filter for legal-za pack, then
                  // restrict to acceptance-eligible — this is the Phase 28 AcceptanceRequest
                  // template-picker seam.
                  List<DocumentTemplate> acceptanceEligibleLegalTemplates =
                      documentTemplateRepository.findByActiveTrueOrderBySortOrder().stream()
                          .filter(t -> PACK_ID.equals(t.getPackId()))
                          .filter(DocumentTemplate::isAcceptanceEligible)
                          .toList();

                  var keys =
                      acceptanceEligibleLegalTemplates.stream()
                          .map(DocumentTemplate::getPackTemplateKey)
                          .toList();

                  assertThat(keys).contains("offer-to-purchase", "power-of-attorney-transfer");
                  assertThat(keys).doesNotContain("deed-of-transfer");
                  assertThat(keys).doesNotContain("bond-cancellation-instruction");
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
