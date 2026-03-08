package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class RequestPackSeeder {

  private static final Logger log = LoggerFactory.getLogger(RequestPackSeeder.class);
  private static final String PACK_LOCATION = "classpath:request-packs/*.json";

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final RequestTemplateRepository requestTemplateRepository;
  private final RequestTemplateItemRepository requestTemplateItemRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TenantTransactionHelper tenantTransactionHelper;

  public RequestPackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      RequestTemplateRepository requestTemplateRepository,
      RequestTemplateItemRepository requestTemplateItemRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.requestTemplateRepository = requestTemplateRepository;
    this.requestTemplateItemRepository = requestTemplateItemRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.tenantTransactionHelper = tenantTransactionHelper;
  }

  public void seedPacksForTenant(String tenantId, String orgId) {
    tenantTransactionHelper.executeInTenantTransaction(tenantId, orgId, t -> doSeedPacks(t));
  }

  private void doSeedPacks(String tenantId) {
    List<RequestPackDefinition> packs = loadPacks();
    if (packs.isEmpty()) {
      log.info("No request packs found on classpath for tenant {}", tenantId);
      return;
    }

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings("USD");
                  return orgSettingsRepository.save(newSettings);
                });

    for (RequestPackDefinition pack : packs) {
      if (isPackAlreadyApplied(settings, pack.packId())) {
        log.info(
            "Request pack {} already applied for tenant {}, skipping", pack.packId(), tenantId);
        continue;
      }

      applyPack(pack);
      settings.recordRequestPackApplication(pack.packId(), pack.version());
      log.info(
          "Applied request pack {} v{} for tenant {}", pack.packId(), pack.version(), tenantId);
    }

    orgSettingsRepository.save(settings);
  }

  private List<RequestPackDefinition> loadPacks() {
    try {
      Resource[] resources = resourceResolver.getResources(PACK_LOCATION);
      return Arrays.stream(resources)
          .map(
              resource -> {
                try {
                  String content = resource.getContentAsString(StandardCharsets.UTF_8);
                  return objectMapper.readValue(content, RequestPackDefinition.class);
                } catch (Exception e) {
                  throw new IllegalStateException(
                      "Failed to parse request pack: " + resource.getFilename(), e);
                }
              })
          .toList();
    } catch (IOException e) {
      log.warn("Failed to scan for request packs at {}", PACK_LOCATION, e);
      return List.of();
    }
  }

  private boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getRequestPackStatus() == null) {
      return false;
    }
    return settings.getRequestPackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  private void applyPack(RequestPackDefinition pack) {
    var template = new RequestTemplate(pack.name(), pack.description(), TemplateSource.PLATFORM);
    template.setPackId(pack.packId());
    template = requestTemplateRepository.save(template);

    for (var itemDef : pack.items()) {
      var item =
          new RequestTemplateItem(
              template.getId(),
              itemDef.name(),
              itemDef.description(),
              ResponseType.valueOf(itemDef.responseType()),
              itemDef.required(),
              itemDef.fileTypeHints(),
              itemDef.sortOrder());
      requestTemplateItemRepository.save(item);
    }
  }
}
