package io.b2mash.b2b.b2bstrawman.collections;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The documented default {@link CollectionsAdvisor} (ADR-329): contributes nothing. Unlike the
 * single-bean {@code ReminderComposer} seam (no-op superseded via {@code @Primary}), advisors are
 * LIST-injected — every bean contributes — so this plain {@code @Component} coexists harmlessly
 * with vertical advisors (e.g. {@code TrustAwareCollectionsAdvisor}); no {@code @Primary}, no
 * conditions.
 *
 * <p>Spring would inject an empty list if no advisor beans existed at all; this bean ships anyway
 * as the seam's existence proof and the named default in ADR-329.
 */
@Component
public class NoOpCollectionsAdvisor implements CollectionsAdvisor {

  @Override
  public List<CollectionsAdvice> adviseFor(UUID customerId) {
    return List.of();
  }
}
