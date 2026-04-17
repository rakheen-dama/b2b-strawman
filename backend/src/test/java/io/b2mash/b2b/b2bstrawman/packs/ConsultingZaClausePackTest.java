package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.clause.ClauseSource;
import io.b2mash.b2b.b2bstrawman.clause.TemplateClause;
import io.b2mash.b2b.b2bstrawman.clause.TemplateClauseRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the {@code consulting-za-clauses} clause pack: asserts the 8 agency clauses
 * seed for a {@code consulting-za} tenant via {@code ClausePackSeeder}'s classpath scan and that
 * the {@code statement-of-work} and {@code engagement-letter} templates from Epic 482's template
 * pack receive the expected clause associations with the correct {@code required} flags.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultingZaClausePackTest {

  private static final String ORG_ID = "org_czcp_test";

  private static final String PACK_ID = "consulting-za-clauses";

  private static final String TEMPLATE_PACK_ID = "consulting-za";

  private static final List<String> EXPECTED_CLAUSE_SLUGS =
      List.of(
          "consulting-ip-ownership",
          "consulting-revision-rounds",
          "consulting-kill-fee",
          "consulting-nda-mutual",
          "consulting-payment-terms",
          "consulting-change-requests",
          "consulting-third-party-costs",
          "consulting-termination");

  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TemplateClauseRepository templateClauseRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(
        ORG_ID, "Consulting ZA Clause Pack Test Org", "consulting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void createsEightConsultingClauses() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses =
                      clauseRepository.findByPackIdAndSourceAndActiveTrue(
                          PACK_ID, ClauseSource.SYSTEM);
                  assertThat(clauses).hasSize(8);
                }));
  }

  @Test
  void allExpectedSlugsArePresent() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var slugs =
                      clauseRepository
                          .findByPackIdAndSourceAndActiveTrue(PACK_ID, ClauseSource.SYSTEM)
                          .stream()
                          .map(c -> c.getSlug())
                          .toList();
                  assertThat(slugs).containsExactlyInAnyOrderElementsOf(EXPECTED_CLAUSE_SLUGS);
                }));
  }

  @Test
  void statementOfWorkTemplateHasSevenClauseAssociationsWithThreeRequired() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var sowTemplate =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(TEMPLATE_PACK_ID, "statement-of-work")
                          .orElseThrow();
                  var associations =
                      templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(
                          sowTemplate.getId());
                  assertThat(associations).hasSize(7);

                  Set<UUID> requiredClauseIds =
                      associations.stream()
                          .filter(TemplateClause::isRequired)
                          .map(TemplateClause::getClauseId)
                          .collect(Collectors.toSet());

                  var paymentTerms =
                      clauseRepository.findBySlug("consulting-payment-terms").orElseThrow();
                  var ipOwnership =
                      clauseRepository.findBySlug("consulting-ip-ownership").orElseThrow();
                  var changeRequests =
                      clauseRepository.findBySlug("consulting-change-requests").orElseThrow();

                  assertThat(requiredClauseIds)
                      .containsExactlyInAnyOrder(
                          paymentTerms.getId(), ipOwnership.getId(), changeRequests.getId());

                  // Full-membership assertion: all 7 associated clauseIds must match the expected
                  // set declared in the clause pack's templateAssociations for statement-of-work.
                  Set<UUID> allAssociatedClauseIds =
                      associations.stream()
                          .map(TemplateClause::getClauseId)
                          .collect(Collectors.toSet());
                  var revisionRounds =
                      clauseRepository.findBySlug("consulting-revision-rounds").orElseThrow();
                  var killFee = clauseRepository.findBySlug("consulting-kill-fee").orElseThrow();
                  var thirdPartyCosts =
                      clauseRepository.findBySlug("consulting-third-party-costs").orElseThrow();
                  var termination =
                      clauseRepository.findBySlug("consulting-termination").orElseThrow();
                  assertThat(allAssociatedClauseIds)
                      .containsExactlyInAnyOrder(
                          paymentTerms.getId(),
                          ipOwnership.getId(),
                          changeRequests.getId(),
                          revisionRounds.getId(),
                          killFee.getId(),
                          thirdPartyCosts.getId(),
                          termination.getId());
                }));
  }

  @Test
  void engagementLetterTemplateHasFiveClauseAssociationsWithThreeRequired() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var letterTemplate =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(TEMPLATE_PACK_ID, "engagement-letter")
                          .orElseThrow();
                  var associations =
                      templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(
                          letterTemplate.getId());
                  assertThat(associations).hasSize(5);

                  Set<UUID> requiredClauseIds =
                      associations.stream()
                          .filter(TemplateClause::isRequired)
                          .map(TemplateClause::getClauseId)
                          .collect(Collectors.toSet());

                  var paymentTerms =
                      clauseRepository.findBySlug("consulting-payment-terms").orElseThrow();
                  var termination =
                      clauseRepository.findBySlug("consulting-termination").orElseThrow();
                  var ndaMutual =
                      clauseRepository.findBySlug("consulting-nda-mutual").orElseThrow();

                  assertThat(requiredClauseIds)
                      .containsExactlyInAnyOrder(
                          paymentTerms.getId(), termination.getId(), ndaMutual.getId());

                  // Full-membership assertion: all 5 associated clauseIds must match the expected
                  // set declared in the clause pack's templateAssociations for engagement-letter.
                  Set<UUID> allAssociatedClauseIds =
                      associations.stream()
                          .map(TemplateClause::getClauseId)
                          .collect(Collectors.toSet());
                  var ipOwnership =
                      clauseRepository.findBySlug("consulting-ip-ownership").orElseThrow();
                  var changeRequests =
                      clauseRepository.findBySlug("consulting-change-requests").orElseThrow();
                  assertThat(allAssociatedClauseIds)
                      .containsExactlyInAnyOrder(
                          paymentTerms.getId(),
                          termination.getId(),
                          ndaMutual.getId(),
                          ipOwnership.getId(),
                          changeRequests.getId());
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
