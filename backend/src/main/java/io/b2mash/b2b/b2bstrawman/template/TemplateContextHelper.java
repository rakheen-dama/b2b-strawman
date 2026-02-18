package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagRepository;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  private final OrgSettingsRepository orgSettingsRepository;
  private final MemberRepository memberRepository;
  private final EntityTagRepository entityTagRepository;
  private final TagRepository tagRepository;
  private final S3PresignedUrlService s3PresignedUrlService;

  public TemplateContextHelper(
      OrgSettingsRepository orgSettingsRepository,
      MemberRepository memberRepository,
      EntityTagRepository entityTagRepository,
      TagRepository tagRepository,
      S3PresignedUrlService s3PresignedUrlService) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.memberRepository = memberRepository;
    this.entityTagRepository = entityTagRepository;
    this.tagRepository = tagRepository;
    this.s3PresignedUrlService = s3PresignedUrlService;
  }

  /** Builds the org context map from OrgSettings (currency, branding, logo URL). */
  public Map<String, Object> buildOrgContext() {
    var orgMap = new LinkedHashMap<String, Object>();
    orgSettingsRepository
        .findForCurrentTenant()
        .ifPresentOrElse(
            settings -> {
              orgMap.put("defaultCurrency", settings.getDefaultCurrency());
              orgMap.put("brandColor", settings.getBrandColor());
              orgMap.put("documentFooterText", settings.getDocumentFooterText());

              if (settings.getLogoS3Key() != null && !settings.getLogoS3Key().isBlank()) {
                try {
                  var result = s3PresignedUrlService.generateDownloadUrl(settings.getLogoS3Key());
                  orgMap.put("logoUrl", result.url());
                } catch (Exception e) {
                  log.warn("Failed to generate logo URL for key: {}", settings.getLogoS3Key(), e);
                  orgMap.put("logoUrl", null);
                }
              } else {
                orgMap.put("logoUrl", null);
              }
            },
            () -> {
              orgMap.put("defaultCurrency", null);
              orgMap.put("brandColor", null);
              orgMap.put("documentFooterText", null);
              orgMap.put("logoUrl", null);
            });
    return orgMap;
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
