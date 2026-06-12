package io.b2mash.b2b.b2bstrawman.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards the all-null-embedded NPE risk introduced by the Wave 3.3 embeddable refactor. Hibernate
 * materialises a NULL embedded object when every mapped column of the group is NULL. An OLD {@code
 * org_settings} row written before the relevant columns existed can therefore reload with a null
 * {@code branding}/{@code portal} reference — and any caller doing {@code
 * settings.getBranding().getX()} would NPE.
 *
 * <p>This test nulls out every nullable branding + portal column via raw SQL (simulating such a
 * legacy row) — all three branding columns are nullable, so the branding embedded materialises as
 * NULL (the genuine NPE risk). It then reloads the entity through the repository on a fresh tenant
 * connection and asserts the group accessors return non-null objects whose getters yield null
 * without throwing. The lazy-initialising {@link OrgSettings#getBranding()} / {@link
 * OrgSettings#getPortal()} fallback is what makes this safe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgSettingsEmbeddableNullReloadTest {
  private static final String ORG_ID = "org_orgsettings_null_reload";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private DataSource dataSource;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "OrgSettings Null Reload Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_onr_owner", "onr_owner@test.com", "ONR Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void allNullEmbeddedGroups_reloadWithoutNpe() throws Exception {
    var idRef = new AtomicReference<UUID>();

    // 1. Persist a settings row inside the tenant (own connection / search_path).
    runInTenant(() -> idRef.set(orgSettingsRepository.save(new OrgSettings("USD")).getId()));
    UUID id = idRef.get();

    // 2. Null out the nullable branding + portal columns via raw SQL — simulating a legacy row.
    //    All three branding columns are nullable, so Hibernate materialises a NULL branding
    //    embedded (the genuine NPE risk). The portal group's portal_notification_doc_types column
    //    is NOT NULL (V117 JSONB DEFAULT) and cannot be nulled — it is set to an empty array, which
    //    still exercises the null-safe portal accessors against an otherwise-blank group.
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SET search_path TO " + tenantSchema);
      int updated =
          stmt.executeUpdate(
              "UPDATE org_settings SET "
                  + "logo_s3_key = NULL, brand_color = NULL, document_footer_text = NULL, "
                  + "portal_retainer_member_display = NULL, portal_digest_cadence = NULL, "
                  + "digest_last_sent_at = NULL, portal_notification_doc_types = '[]'::jsonb "
                  + "WHERE id = '"
                  + id
                  + "'");
      assertThat(updated).as("the null-out UPDATE must hit exactly one row").isEqualTo(1);
    }

    // 3. Reload on a fresh tenant connection (no first-level cache carry-over) and assert the
    //    group accessors are non-null and their getters do not NPE.
    runInTenant(
        () -> {
          var reloaded = orgSettingsRepository.findById(id).orElseThrow();
          assertThatCode(
                  () -> {
                    assertThat(reloaded.getBranding()).isNotNull();
                    assertThat(reloaded.getBranding().getLogoS3Key()).isNull();
                    assertThat(reloaded.getBranding().getBrandColor()).isNull();
                    assertThat(reloaded.getBranding().getDocumentFooterText()).isNull();

                    assertThat(reloaded.getPortal()).isNotNull();
                    assertThat(reloaded.getPortal().getPortalRetainerMemberDisplay()).isNull();
                    assertThat(reloaded.getPortal().getPortalDigestCadence()).isNull();
                    assertThat(reloaded.getPortal().getDigestLastSentAt()).isNull();
                    // null-safe accessor returns empty list, never null
                    assertThat(reloaded.getPortal().getPortalNotificationDocTypes()).isEmpty();

                    // Effective-getter fallbacks must still work on the all-null group.
                    assertThat(reloaded.getPortal().getEffectivePortalDigestCadence())
                        .isEqualTo(PortalDigestCadence.WEEKLY);
                    assertThat(reloaded.getPortal().getEffectivePortalRetainerMemberDisplay())
                        .isEqualTo(PortalRetainerMemberDisplay.FIRST_NAME_ROLE);
                  })
              .doesNotThrowAnyException();
        });
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
