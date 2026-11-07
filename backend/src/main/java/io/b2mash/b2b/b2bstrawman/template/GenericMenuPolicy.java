package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Which templates the generic Generate menus (matter, client, fee note) offer (G89 632A(a)(c),
 * D11). A template is hidden there when it is a {@link DedicatedFlowTemplate}, or when it is a
 * pack-seeded row (PLATFORM, not a clone) whose classpath pack entry lists the tenant's vertical
 * profile in {@code genericMenuExcludedProfiles} — e.g. the common pack's Standard Engagement
 * Letter and Matter Summary Report on legal-za, which ships its own engagement letters. A firm's
 * clone of an excluded template is the firm's own choice and stays offered. Nothing is written:
 * Settings › Documents still lists every template.
 */
@Component
public class GenericMenuPolicy {

  /** One pack template, by its natural key. */
  record PackTemplateRef(String packId, String templateKey) {}

  private final TemplatePackSeeder templatePackSeeder;
  private final OrgSettingsRepository orgSettingsRepository;

  /** Classpath packs never change at runtime: (packId, templateKey, profile) exclusions, lazily. */
  private volatile List<Exclusion> exclusions;

  private record Exclusion(PackTemplateRef template, String profile) {}

  public GenericMenuPolicy(
      TemplatePackSeeder templatePackSeeder, OrgSettingsRepository orgSettingsRepository) {
    this.templatePackSeeder = templatePackSeeder;
    this.orgSettingsRepository = orgSettingsRepository;
  }

  /** A view of the policy for the current tenant's vertical profile, for one listing pass. */
  public ForTenant forCurrentTenant() {
    String profile =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getVerticalProfile)
            .orElse(null);
    Set<PackTemplateRef> excluded = new HashSet<>();
    if (profile != null) {
      for (Exclusion e : exclusions()) {
        if (profile.equals(e.profile())) {
          excluded.add(e.template());
        }
      }
    }
    return new ForTenant(Set.copyOf(excluded));
  }

  private List<Exclusion> exclusions() {
    List<Exclusion> loaded = exclusions;
    if (loaded == null) {
      loaded =
          templatePackSeeder.getAvailablePacks().stream()
              .flatMap(
                  lp ->
                      lp.definition().templates().stream()
                          .flatMap(
                              t ->
                                  t.genericMenuExcludedProfiles().stream()
                                      .map(
                                          profile ->
                                              new Exclusion(
                                                  new PackTemplateRef(
                                                      lp.definition().packId(), t.templateKey()),
                                                  profile))))
              .toList();
      exclusions = loaded;
    }
    return loaded;
  }

  /** The policy bound to one tenant's profile. */
  public static final class ForTenant {

    private final Set<PackTemplateRef> excluded;

    ForTenant(Set<PackTemplateRef> excluded) {
      this.excluded = excluded;
    }

    /** True when the generic Generate menus must not offer {@code template}. */
    public boolean hiddenFromGenericMenus(DocumentTemplate template) {
      return DedicatedFlowTemplate.of(template).isPresent() || excludedForProfile(template);
    }

    private boolean excludedForProfile(DocumentTemplate template) {
      return template.getSource() == TemplateSource.PLATFORM
          && template.getSourceTemplateId() == null
          && template.getPackId() != null
          && excluded.contains(
              new PackTemplateRef(template.getPackId(), template.getPackTemplateKey()));
    }
  }
}
