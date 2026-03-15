package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class RequestPackSeeder extends AbstractPackSeeder<RequestPackDefinition> {

  private static final String PACK_LOCATION = "classpath:request-packs/*.json";

  private final RequestTemplateRepository requestTemplateRepository;
  private final RequestTemplateItemRepository requestTemplateItemRepository;

  public RequestPackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      RequestTemplateRepository requestTemplateRepository,
      RequestTemplateItemRepository requestTemplateItemRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
    this.requestTemplateRepository = requestTemplateRepository;
    this.requestTemplateItemRepository = requestTemplateItemRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<RequestPackDefinition> getPackDefinitionType() {
    return RequestPackDefinition.class;
  }

  @Override
  protected String getPackTypeName() {
    return "request";
  }

  @Override
  protected String getPackId(RequestPackDefinition pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(RequestPackDefinition pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected String getVerticalProfile(RequestPackDefinition pack) {
    return pack.verticalProfile();
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getRequestPackStatus() == null) {
      return false;
    }
    return settings.getRequestPackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, RequestPackDefinition pack) {
    settings.recordRequestPackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(RequestPackDefinition pack, Resource packResource, String tenantId) {
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
