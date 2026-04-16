package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PackCatalogControllerIntegrationTest {

  private static final String ORG_ID = "org_pcc_test";
  private static final String PACK_ID = "common";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Pack Catalog Controller Test Org", null);
    memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_pcc_owner", "pcc_owner@test.com", "PCC Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  // --- 477.3: Catalog GET integration tests ---

  @Test
  @Order(1)
  void catalogFilteredByProfile() throws Exception {
    // Tenant has no vertical profile, so only universal packs (verticalProfile == null) returned
    mockMvc
        .perform(get("/api/packs/catalog").with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$[*].verticalProfile", everyItem(nullValue())));
  }

  @Test
  @Order(2)
  void catalogWithAllTrue() throws Exception {
    // all=true returns all packs including profile-specific ones
    mockMvc
        .perform(
            get("/api/packs/catalog?all=true")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$[0].packId").isString())
        .andExpect(jsonPath("$[0].name").isString())
        .andExpect(jsonPath("$[0].version").isString())
        .andExpect(jsonPath("$[0].type").isString())
        .andExpect(jsonPath("$[0].itemCount").isNumber());
  }

  // --- 477.4: Install/uninstall endpoint integration tests ---

  @Test
  @Order(3)
  void installReturns200WithResponse() throws Exception {
    // "common" is pre-installed by provisioning, so install is idempotent and returns existing
    mockMvc
        .perform(
            post("/api/packs/" + PACK_ID + "/install")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.packId").value(PACK_ID))
        .andExpect(jsonPath("$.packType").isString())
        .andExpect(jsonPath("$.packVersion").isString())
        .andExpect(jsonPath("$.packName").isString())
        .andExpect(jsonPath("$.installedAt").isString())
        .andExpect(jsonPath("$.itemCount").isNumber());
  }

  @Test
  @Order(4)
  void deleteOnUneditedPackReturns204() throws Exception {
    // The "common" pack was installed by provisioning (unedited), so uninstall should succeed
    mockMvc
        .perform(
            delete("/api/packs/" + PACK_ID).with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isNoContent());
  }

  // --- 477.5: Error path integration tests ---

  @Test
  @Order(5)
  void installNonexistentPackReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/packs/nonexistent-pack/install")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(jsonPath("$.detail").isString());
  }

  @Test
  @Order(6)
  void deleteBlockedUninstallReturns409() throws Exception {
    // Re-install the common pack first (was uninstalled in Order 4)
    mockMvc
        .perform(
            post("/api/packs/" + PACK_ID + "/install")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isOk());

    // Edit a template to trigger content hash mismatch (blocking uninstall)
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  var templates =
                      documentTemplateRepository.findBySourcePackInstallId(install.getId());
                  assertThat(templates)
                      .as("Pack '%s' should have at least one linked template", PACK_ID)
                      .isNotEmpty();
                  DocumentTemplate template = templates.getFirst();
                  template.setCss(
                      (template.getCss() != null ? template.getCss() : "") + " /* EDITED */");
                  documentTemplateRepository.save(template);
                }));

    // Now DELETE should return 409
    mockMvc
        .perform(
            delete("/api/packs/" + PACK_ID).with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(jsonPath("$.detail").isString());
  }

  // --- 477.6: Uninstall-check endpoint test ---

  @Test
  @Order(7)
  void uninstallCheckReturnsCanUninstallBasedOnEditState() throws Exception {
    // Pack was edited in Order(6), so uninstall-check should return canUninstall: false
    mockMvc
        .perform(
            get("/api/packs/" + PACK_ID + "/uninstall-check")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canUninstall").value(false))
        .andExpect(jsonPath("$.blockingReason", is(notNullValue())));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberId))
        .run(action);
  }
}
