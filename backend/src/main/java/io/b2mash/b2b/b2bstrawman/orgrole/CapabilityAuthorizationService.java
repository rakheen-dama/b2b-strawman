package io.b2mash.b2b.b2bstrawman.orgrole;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Programmatic alternative to {@link RequiresCapability} for inline capability checks. Reads from
 * {@link RequestScopes#CAPABILITIES} which is bound by {@code MemberFilter}.
 */
@Service
public class CapabilityAuthorizationService {

  /** Returns true if the current member has the given capability (or "ALL"). */
  public boolean hasCapability(String capability) {
    return RequestScopes.hasCapability(capability);
  }

  /**
   * Throws {@link AccessDeniedException} if the current member lacks the given capability.
   *
   * @throws AccessDeniedException if the capability is not present
   */
  public void requireCapability(String capability) {
    if (!hasCapability(capability)) {
      throw new AccessDeniedException("Missing required capability: " + capability);
    }
  }
}
