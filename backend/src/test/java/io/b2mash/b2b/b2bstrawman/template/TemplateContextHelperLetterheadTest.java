package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagRepository;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Letterhead keys exposed by {@link TemplateContextHelper#buildOrgContext()} (Phase 88 626B, U8,
 * ADR-353): {@code org.address}, {@code org.addressLines}, {@code org.phone}, {@code org.email},
 * {@code org.website}, {@code org.bankingDetails.*}, {@code org.signatory.*} and the deprecated
 * {@code org.principal_attorney} alias. Group maps are {@code null} when every member is blank so a
 * {@code conditionalBlock isNotEmpty} on the group hides its label.
 */
@ExtendWith(MockitoExtension.class)
class TemplateContextHelperLetterheadTest {

  private static final List<String> LETTERHEAD_KEYS =
      List.of(
          "address",
          "addressLines",
          "phone",
          "email",
          "website",
          "bankingDetails",
          "signatory",
          "principal_attorney");

  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private EntityTagRepository entityTagRepository;
  @Mock private TagRepository tagRepository;
  @Mock private StorageService storageService;
  @Mock private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private FieldDefinitionRepository fieldDefinitionRepository;

  @InjectMocks private TemplateContextHelper helper;

  private Map<String, Object> buildWith(OrgSettings settings) {
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.ofNullable(settings));
    return helper.buildOrgContext();
  }

  private static OrgSettings fullyFilled() {
    var settings = new OrgSettings("ZAR");
    settings
        .getLetterhead()
        .updateLetterhead(
            "12 Rissik Street",
            "Suite 4",
            null,
            "Johannesburg",
            "2001",
            "South Africa",
            "+27 11 555 0100",
            "office@ndlovu.co.za",
            "https://ndlovu.co.za",
            "FNB",
            "250655",
            "62812345678",
            "Cheque",
            "Use the fee note number as reference",
            "Thandiwe Khumalo",
            "Director");
    return settings;
  }

  @Test
  void letterhead_keys_present_when_set() {
    var org = buildWith(fullyFilled());

    assertThat(org.get("address"))
        .isEqualTo("12 Rissik Street, Suite 4, Johannesburg 2001, South Africa");
    assertThat(org.get("addressLines"))
        .isEqualTo(List.of("12 Rissik Street", "Suite 4", "Johannesburg 2001", "South Africa"));
    assertThat(org.get("phone")).isEqualTo("+27 11 555 0100");
    assertThat(org.get("email")).isEqualTo("office@ndlovu.co.za");
    assertThat(org.get("website")).isEqualTo("https://ndlovu.co.za");

    @SuppressWarnings("unchecked")
    var banking = (Map<String, Object>) org.get("bankingDetails");
    assertThat(banking)
        .containsExactly(
            Map.entry("bankName", "FNB"),
            Map.entry("branchCode", "250655"),
            Map.entry("accountNumber", "62812345678"),
            Map.entry("accountType", "Cheque"),
            Map.entry("referenceNote", "Use the fee note number as reference"));

    @SuppressWarnings("unchecked")
    var signatory = (Map<String, Object>) org.get("signatory");
    assertThat(signatory)
        .containsExactly(
            Map.entry("name", "Thandiwe Khumalo"), Map.entry("designation", "Director"));
  }

  @Test
  void groups_null_and_address_empty_when_nothing_set() {
    var org = buildWith(new OrgSettings("ZAR"));

    assertThat(org).containsKeys(LETTERHEAD_KEYS.toArray(String[]::new));
    assertThat(org.get("address")).isEqualTo("");
    assertThat(org.get("addressLines")).isEqualTo(List.of());
    assertThat(org.get("phone")).isNull();
    assertThat(org.get("email")).isNull();
    assertThat(org.get("website")).isNull();
    assertThat(org.get("bankingDetails")).isNull();
    assertThat(org.get("signatory")).isNull();
    assertThat(org.get("principal_attorney")).isNull();

    // The renderer's isNotEmpty must read the empty groups as empty (626D's conditionalBlock).
    var renderer = new TiptapRenderer("");
    assertThat(renderer.evaluateCondition(org.get("bankingDetails"), "isNotEmpty", null)).isFalse();
    assertThat(renderer.evaluateCondition(org.get("signatory"), "isNotEmpty", null)).isFalse();
    assertThat(renderer.evaluateCondition(org.get("address"), "isNotEmpty", null)).isFalse();
  }

  @Test
  void whitespace_only_values_are_treated_as_blank() {
    var settings = new OrgSettings("ZAR");
    var letterhead = settings.getLetterhead();
    letterhead.setPhone("   ");
    letterhead.setBankName(" ");
    letterhead.setSignatoryName("\t");

    var org = buildWith(settings);

    assertThat(org.get("phone")).isNull();
    assertThat(org.get("bankingDetails")).isNull();
    assertThat(org.get("signatory")).isNull();
    assertThat(org.get("principal_attorney")).isNull();
  }

  @Test
  void partial_banking_group_is_a_map_with_null_members() {
    var settings = new OrgSettings("ZAR");
    settings.getLetterhead().setBankName("FNB");

    var org = buildWith(settings);

    @SuppressWarnings("unchecked")
    var banking = (Map<String, Object>) org.get("bankingDetails");
    assertThat(banking).isNotNull();
    assertThat(banking)
        .containsOnlyKeys(
            "bankName", "branchCode", "accountNumber", "accountType", "referenceNote");
    assertThat(banking.get("bankName")).isEqualTo("FNB");
    assertThat(banking.get("branchCode")).isNull();
    assertThat(banking.get("accountNumber")).isNull();
    assertThat(banking.get("accountType")).isNull();
    assertThat(banking.get("referenceNote")).isNull();
    assertThat(org.get("signatory")).isNull();
  }

  @Test
  void principal_attorney_aliases_signatory_name() {
    var org = buildWith(fullyFilled());
    assertThat(org.get("principal_attorney")).isEqualTo("Thandiwe Khumalo");

    // Designation alone still yields a signatory group, but the alias stays null.
    var settings = new OrgSettings("ZAR");
    settings.getLetterhead().setSignatoryDesignation("Director");
    var designationOnly = buildWith(settings);
    assertThat(designationOnly.get("signatory")).isNotNull();
    assertThat(designationOnly.get("principal_attorney")).isNull();
  }

  @Test
  void no_settings_row_branch_has_letterhead_keys() {
    var org = buildWith(null);

    assertThat(org).containsKeys(LETTERHEAD_KEYS.toArray(String[]::new));
    assertThat(org.get("address")).isEqualTo("");
    assertThat(org.get("addressLines")).isEqualTo(List.of());
    assertThat(org.get("phone")).isNull();
    assertThat(org.get("email")).isNull();
    assertThat(org.get("website")).isNull();
    assertThat(org.get("bankingDetails")).isNull();
    assertThat(org.get("signatory")).isNull();
    assertThat(org.get("principal_attorney")).isNull();
  }

  @Test
  void existing_org_keys_unchanged() {
    var settings = fullyFilled();
    settings.getBranding().setBrandColor("#0f766e");
    settings.getBranding().setDocumentFooterText("Confidential");
    settings.getTax().setTaxRegistrationNumber("4123456789");

    var org = buildWith(settings);

    assertThat(org.get("name")).isEqualTo("Unknown Organization");
    assertThat(org.get("defaultCurrency")).isEqualTo("ZAR");
    assertThat(org.get("brandColor")).isEqualTo("#0f766e");
    assertThat(org.get("documentFooterText")).isEqualTo("Confidential");
    assertThat(org.get("taxRegistrationNumber")).isEqualTo("4123456789");
    assertThat(org).containsKey("logoUrl");
    assertThat(org.get("logoUrl")).isNull();

    var noRow = buildWith(null);
    assertThat(noRow)
        .containsKeys(
            "name",
            "defaultCurrency",
            "brandColor",
            "documentFooterText",
            "taxRegistrationNumber",
            "logoUrl");
  }

  // --- Logo as a data: URI (Phase 88 626D, ruling R-1) ---

  private static final String PNG_KEY = "org/tenant_x/branding/logo.png";

  private static OrgSettings withLogoKey(String key) {
    var settings = new OrgSettings("ZAR");
    settings.getBranding().setLogoS3Key(key);
    return settings;
  }

  private void presigns(String key, String url) {
    when(storageService.generateDownloadUrl(eq(key), any()))
        .thenReturn(new PresignedUrl(url, Instant.now()));
  }

  /**
   * 626D review F-2: building a context never downloads the logo — readiness checks, required-field
   * validation and DOCX merge build contexts without rendering an image. Non-image consumers see
   * the presigned URL.
   */
  @Test
  void building_the_context_never_downloads_the_logo() {
    presigns(PNG_KEY, "https://s3.test/logo.png?sig=1");

    var org = buildWith(withLogoKey(PNG_KEY));

    assertThat(org.get("logoUrl")).isInstanceOf(InlineLogo.class);
    assertThat(String.valueOf(org.get("logoUrl"))).isEqualTo("https://s3.test/logo.png?sig=1");
    verify(storageService, never()).download(anyString());
  }

  @Test
  void logo_bytes_available_render_as_a_png_data_uri_downloaded_once() {
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    presigns(PNG_KEY, "https://s3.test/logo.png?sig=1");
    when(storageService.download(PNG_KEY)).thenReturn(png);

    var logo = (InlineLogo) buildWith(withLogoKey(PNG_KEY)).get("logoUrl");

    String expected = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    assertThat(logo.src()).isEqualTo(expected);
    assertThat(logo.src()).isEqualTo(expected);
    verify(storageService, times(1)).download(PNG_KEY);
  }

  @Test
  void jpeg_key_renders_a_jpeg_data_uri() {
    String key = "org/tenant_x/branding/logo.JPG";
    presigns(key, "https://s3.test/logo.jpg");
    when(storageService.download(key)).thenReturn(new byte[] {(byte) 0xFF, (byte) 0xD8});

    var logo = (InlineLogo) buildWith(withLogoKey(key)).get("logoUrl");

    assertThat(logo.src()).isEqualTo("data:image/jpeg;base64,/9g=");
  }

  @Test
  void logo_download_failure_falls_back_to_the_presigned_url() {
    presigns(PNG_KEY, "https://s3.test/logo.png?sig=1");
    when(storageService.download(PNG_KEY)).thenThrow(new RuntimeException("Key not found"));

    var logo = (InlineLogo) buildWith(withLogoKey(PNG_KEY)).get("logoUrl");

    assertThat(logo.src()).isEqualTo("https://s3.test/logo.png?sig=1");
    assertThat(logo.src()).isEqualTo("https://s3.test/logo.png?sig=1");
    verify(storageService, times(1)).download(PNG_KEY);
  }

  @Test
  void svg_logo_is_never_inlined_and_uses_the_presigned_url() {
    String key = "org/tenant_x/branding/logo.svg";
    presigns(key, "https://s3.test/logo.svg");

    var org = buildWith(withLogoKey(key));

    assertThat(org.get("logoUrl")).isEqualTo("https://s3.test/logo.svg");
    verify(storageService, never()).download(anyString());
  }

  @Test
  void presign_failure_yields_no_logo() {
    when(storageService.generateDownloadUrl(eq(PNG_KEY), any()))
        .thenThrow(new RuntimeException("presigner down"));

    var org = buildWith(withLogoKey(PNG_KEY));

    assertThat(org).containsKey("logoUrl");
    assertThat(org.get("logoUrl")).isNull();
    verify(storageService, never()).download(anyString());
  }
}
