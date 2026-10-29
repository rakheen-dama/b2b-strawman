package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class RequestPackSeeder extends AbstractPackSeeder<RequestPackDefinition> {

  private static final String PACK_LOCATION = "classpath:request-packs/*.json";

  /** ADR-356 reconcile entity of a request pack's template (key = the pack id). */
  static final String TEMPLATE_ENTITY = "request_template";

  private final RequestTemplateRepository requestTemplateRepository;
  private final RequestTemplateItemRepository requestTemplateItemRepository;

  public RequestPackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      RequestTemplateRepository requestTemplateRepository,
      RequestTemplateItemRepository requestTemplateItemRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      PackVersionGate packVersionGate) {
    super(
        resourceResolver,
        objectMapper,
        orgSettingsRepository,
        tenantTransactionHelper,
        packVersionGate);
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
  protected PackType getPackType() {
    return PackType.REQUEST;
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
    if (settings.getPackStatus().getRequestPackStatus() == null) {
      return false;
    }
    return settings.getPackStatus().getRequestPackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, RequestPackDefinition pack) {
    settings.getPackStatus().recordRequestPackApplication(pack.packId(), pack.version());
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

  /**
   * ADR-356 reconcile target (628C): {@value #TEMPLATE_ENTITY}, {@code key} = this pack's id (a
   * request pack seeds exactly one template), {@code field} {@code name} or {@code description},
   * string {@code to} equal to the pack's shipped {@code name} / {@code description}. The target is
   * the pack's single {@code PLATFORM} row ({@code pack_id} = this pack); {@code CUSTOM} rows (firm
   * duplicates) are never written and the one rule keeps firm edits. Unsupported entries and
   * ambiguous rows are WARN-logged and skipped; nothing here throws at boot.
   */
  @Override
  protected Optional<PackReconcileEntry.Target> resolveReconcileTarget(
      RequestPackDefinition pack, PackReconcileEntry entry, Resource packResource) {
    boolean nameField = "name".equals(entry.field());
    if (!TEMPLATE_ENTITY.equals(entry.entity())
        || !(nameField || "description".equals(entry.field()))
        || !(entry.to() instanceof String to)) {
      return unsupported(pack, entry, "only request_template name/description with a string 'to'");
    }
    if (!pack.packId().equals(entry.key())) {
      return unsupported(pack, entry, "key is not this pack's id");
    }
    if (!to.equals(nameField ? pack.name() : pack.description())) {
      return unsupported(pack, entry, "'to' does not match the shipped value");
    }
    var rows =
        requestTemplateRepository.findByPackId(pack.packId()).stream()
            .filter(t -> t.getSource() == TemplateSource.PLATFORM)
            .toList();
    if (rows.size() > 1) {
      return unsupported(pack, entry, "ambiguous (" + rows.size() + " PLATFORM rows)");
    }
    return rows.stream()
        .findFirst()
        .map(
            t ->
                new PackReconcileEntry.Target() {
                  @Override
                  public Object currentValue() {
                    return nameField ? t.getName() : t.getDescription();
                  }

                  @Override
                  public void apply(Object value) {
                    t.update(
                        nameField ? (String) value : t.getName(),
                        nameField ? t.getDescription() : (String) value);
                  }
                });
  }

  private Optional<PackReconcileEntry.Target> unsupported(
      RequestPackDefinition pack, PackReconcileEntry entry, String reason) {
    log.warn(
        "Reconcile entry ignored: request pack {} entry '{}'.{} (key {}) — {}",
        pack.packId(),
        entry.entity(),
        entry.field(),
        entry.key(),
        reason);
    return Optional.empty();
  }
}
