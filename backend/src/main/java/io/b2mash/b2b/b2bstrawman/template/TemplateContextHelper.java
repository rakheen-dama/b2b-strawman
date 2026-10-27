package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.LetterheadSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagRepository;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared helper methods for building template rendering context. Extracts common logic (org
 * context, generatedBy, tags) that was previously duplicated across context builders.
 */
@Component
public class TemplateContextHelper {

  private static final Logger log = LoggerFactory.getLogger(TemplateContextHelper.class);
  private static final Duration LOGO_URL_EXPIRY = Duration.ofHours(1);

  private final OrgSettingsRepository orgSettingsRepository;
  private final MemberRepository memberRepository;
  private final EntityTagRepository entityTagRepository;
  private final TagRepository tagRepository;
  private final StorageService storageService;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final OrganizationRepository organizationRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;

  public TemplateContextHelper(
      OrgSettingsRepository orgSettingsRepository,
      MemberRepository memberRepository,
      EntityTagRepository entityTagRepository,
      TagRepository tagRepository,
      StorageService storageService,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      OrganizationRepository organizationRepository,
      FieldDefinitionRepository fieldDefinitionRepository) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.memberRepository = memberRepository;
    this.entityTagRepository = entityTagRepository;
    this.tagRepository = tagRepository;
    this.storageService = storageService;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.organizationRepository = organizationRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
  }

  /**
   * Populates the {@code _locale} key in the rendering context based on the tenant's default
   * currency from OrgSettings. Used by context builders so that TiptapRenderer can pass it to
   * VariableFormatter for locale-aware currency/number formatting.
   */
  public void populateLocale(Map<String, Object> context) {
    orgSettingsRepository
        .findForCurrentTenant()
        .ifPresent(
            settings -> {
              String currencyCode = settings.getDefaultCurrency();
              if (currencyCode != null) {
                context.put("_locale", VariableFormatter.resolveLocale(currencyCode));
              }
            });
  }

  /**
   * Builds the org context map from OrgSettings (currency, branding, logo URL, VAT number, firm
   * letterhead) and org name. Every key is present on both branches (settings row or none) so a
   * template's {@code conditionalBlock} sees a null rather than a missing key.
   *
   * <p>Letterhead keys (Phase 88 U8, ADR-353): {@code address} (joined lines, {@code ""} when
   * empty), {@code addressLines} (list — not template-bindable, use {@code address}), {@code
   * phone}, {@code email}, {@code website}, {@code
   * bankingDetails.{bankName,branchCode,accountNumber,accountType,referenceNote}}, {@code
   * signatory.{name,designation}} and the deprecated {@code principal_attorney} alias. See {@link
   * #putLetterheadKeys}.
   */
  public Map<String, Object> buildOrgContext() {
    var orgMap = new LinkedHashMap<String, Object>();

    // Resolve org name from public schema: tenant schema -> clerk org ID -> organization name
    resolveOrgName(orgMap);

    orgSettingsRepository
        .findForCurrentTenant()
        .ifPresentOrElse(
            settings -> {
              orgMap.put("defaultCurrency", settings.getDefaultCurrency());
              orgMap.put("brandColor", settings.getBranding().getBrandColor());
              orgMap.put("documentFooterText", settings.getBranding().getDocumentFooterText());
              orgMap.put("taxRegistrationNumber", settings.getTax().getTaxRegistrationNumber());

              orgMap.put("logoUrl", resolveLogoSrc(settings.getBranding().getLogoS3Key()));
              putLetterheadKeys(orgMap, settings.getLetterhead());
            },
            () -> {
              orgMap.put("defaultCurrency", null);
              orgMap.put("brandColor", null);
              orgMap.put("documentFooterText", null);
              orgMap.put("taxRegistrationNumber", null);
              orgMap.put("logoUrl", null);
              putLetterheadKeys(orgMap, null);
            });
    return orgMap;
  }

  /**
   * The {@code org.logoUrl} value for a branding logo key (Phase 88 626D, ruling R-1): a {@code
   * data:image/png|jpeg;base64,} URI of the stored bytes, so the PDF renderer never fetches the
   * logo over the network (a presigned URL's host is not always reachable from the backend, e.g.
   * the e2e stack) and the browser preview shows the identical string. SVG (no PDF support) or any
   * download failure falls back to the presigned URL; {@code null} when no logo is set or no URL
   * can be generated.
   */
  private String resolveLogoSrc(String logoS3Key) {
    if (logoS3Key == null || logoS3Key.isBlank()) {
      return null;
    }
    String mime = logoMimeType(logoS3Key);
    if (mime != null) {
      try {
        byte[] bytes = storageService.download(logoS3Key);
        if (bytes != null && bytes.length > 0) {
          return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        }
        log.warn("Logo bytes empty for key {}, falling back to the presigned URL", logoS3Key);
      } catch (Exception e) {
        log.warn(
            "Logo bytes unavailable for key {} ({}), falling back to the presigned URL",
            logoS3Key,
            e.getMessage());
      }
    }
    try {
      return storageService.generateDownloadUrl(logoS3Key, LOGO_URL_EXPIRY).url();
    } catch (Exception e) {
      log.warn("Failed to generate logo URL for key: {}", logoS3Key, e);
      return null;
    }
  }

  /** PNG/JPEG MIME type from the logo key's extension; {@code null} for anything else (SVG). */
  private static String logoMimeType(String key) {
    String lower = key.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png")) {
      return "image/png";
    }
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    return null;
  }

  /**
   * Firm letterhead keys (Phase 88 U8, ADR-353). Group maps ({@code bankingDetails}, {@code
   * signatory}) are {@code null} when every member is blank, so a {@code conditionalBlock
   * isNotEmpty} on the group hides its label — the renderer's isNotEmpty stringifies values, so an
   * empty or all-null map would read as non-empty. A partially filled group is a map whose unset
   * members are {@code null}. Scalars are blank-to-null (defence in depth; the settings request
   * already normalises). {@code addressLines} is for programmatic use: a list never renders through
   * a variable node, and an empty list reads as non-empty to isNotEmpty — templates bind and
   * condition on {@code org.address}.
   */
  private static void putLetterheadKeys(Map<String, Object> orgMap, LetterheadSettings letterhead) {
    if (letterhead == null) {
      orgMap.put("address", "");
      orgMap.put("addressLines", List.of());
      orgMap.put("phone", null);
      orgMap.put("email", null);
      orgMap.put("website", null);
      orgMap.put("bankingDetails", null);
      orgMap.put("signatory", null);
      orgMap.put("principal_attorney", null);
      return;
    }
    orgMap.put("address", letterhead.addressJoined());
    orgMap.put("addressLines", letterhead.addressLines());
    orgMap.put("phone", blankToNull(letterhead.getPhone()));
    orgMap.put("email", blankToNull(letterhead.getEmail()));
    orgMap.put("website", blankToNull(letterhead.getWebsite()));

    var banking = new LinkedHashMap<String, Object>();
    banking.put("bankName", blankToNull(letterhead.getBankName()));
    banking.put("branchCode", blankToNull(letterhead.getBankBranchCode()));
    banking.put("accountNumber", blankToNull(letterhead.getBankAccountNumber()));
    banking.put("accountType", blankToNull(letterhead.getBankAccountType()));
    banking.put("referenceNote", blankToNull(letterhead.getPaymentReferenceNote()));
    orgMap.put("bankingDetails", anyValue(banking) ? banking : null);

    var signatory = new LinkedHashMap<String, Object>();
    signatory.put("name", blankToNull(letterhead.getSignatoryName()));
    signatory.put("designation", blankToNull(letterhead.getSignatoryDesignation()));
    orgMap.put("signatory", anyValue(signatory) ? signatory : null);

    // DEPRECATED (Phase 88 626B, ADR-353): alias of org.signatory.name for the legal-za
    // matter-closure-letter, deed-of-transfer and bond-cancellation-instruction templates.
    // Remove after a pack reconcile moves those three templates onto org.signatory.name.
    orgMap.put("principal_attorney", signatory.get("name"));
  }

  private static boolean anyValue(Map<String, Object> group) {
    return group.values().stream().anyMatch(Objects::nonNull);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  private void resolveOrgName(Map<String, Object> orgMap) {
    if (!RequestScopes.TENANT_ID.isBound()) {
      orgMap.put("name", "Unknown Organization");
      return;
    }
    var schemaName = RequestScopes.TENANT_ID.get();
    orgSchemaMappingRepository
        .findBySchemaName(schemaName)
        .flatMap(mapping -> organizationRepository.findByClerkOrgId(mapping.getClerkOrgId()))
        .ifPresentOrElse(
            org -> orgMap.put("name", org.getName()),
            () -> orgMap.put("name", "Unknown Organization"));
  }

  /** Builds the generatedBy map from the member who triggered generation. */
  public Map<String, Object> buildGeneratedByMap(UUID memberId) {
    var generatedBy = new LinkedHashMap<String, Object>();
    memberRepository
        .findById(memberId)
        .ifPresentOrElse(
            member -> {
              generatedBy.put("name", member.getName());
              generatedBy.put("email", member.getEmail());
            },
            () -> {
              generatedBy.put("name", "Unknown");
              generatedBy.put("email", null);
            });
    return generatedBy;
  }

  /**
   * Resolves dropdown custom field values to their display labels. For each DROPDOWN field
   * definition that has options, replaces the stored value (e.g., "MONTHLY_BOOKKEEPING") with the
   * human-readable label (e.g., "Monthly Bookkeeping").
   *
   * <p>This overload creates a fresh cache per call. For batch rendering where multiple entity
   * types are resolved in one buildContext() call, prefer the overload that accepts a shared cache
   * map.
   */
  public Map<String, Object> resolveDropdownLabels(
      Map<String, Object> customFields, EntityType entityType) {
    return resolveDropdownLabels(customFields, entityType, new EnumMap<>(EntityType.class));
  }

  /**
   * Resolves dropdown custom field values to their display labels, using the provided cache to
   * avoid repeated DB queries for the same entity type within a single render pass.
   *
   * @param fieldDefCache per-invocation cache; callers create one {@code EnumMap<EntityType,
   *     List<FieldDefinition>>} at the top of their buildContext() method and pass it to all calls
   */
  public Map<String, Object> resolveDropdownLabels(
      Map<String, Object> customFields,
      EntityType entityType,
      Map<EntityType, List<FieldDefinition>> fieldDefCache) {
    if (customFields == null || customFields.isEmpty()) {
      return customFields;
    }
    var fieldDefs =
        fieldDefCache.computeIfAbsent(
            entityType,
            et -> fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(et));
    var dropdownFields =
        fieldDefs.stream()
            .filter(fd -> fd.getFieldType() == FieldType.DROPDOWN && fd.getOptions() != null)
            .toList();
    if (dropdownFields.isEmpty()) {
      return customFields;
    }

    var resolved = new LinkedHashMap<>(customFields);
    for (var fd : dropdownFields) {
      Object rawValue = resolved.get(fd.getSlug());
      if (rawValue instanceof String strValue) {
        fd.getOptions().stream()
            .filter(opt -> strValue.equals(opt.get("value")))
            .findFirst()
            .ifPresent(opt -> resolved.put(fd.getSlug(), opt.get("label")));
      }
    }
    return resolved;
  }

  /** Builds a list of tag maps for the given entity. */
  public List<Map<String, Object>> buildTagsList(String entityType, UUID entityId) {
    var entityTags = entityTagRepository.findByEntityTypeAndEntityId(entityType, entityId);
    if (entityTags.isEmpty()) {
      return List.of();
    }
    var tagIds = entityTags.stream().map(et -> et.getTagId()).toList();
    var tags = tagRepository.findAllById(tagIds);
    return tags.stream()
        .map(
            tag -> {
              var tagMap = new LinkedHashMap<String, Object>();
              tagMap.put("name", tag.getName());
              tagMap.put("color", tag.getColor());
              return (Map<String, Object>) tagMap;
            })
        .toList();
  }
}
