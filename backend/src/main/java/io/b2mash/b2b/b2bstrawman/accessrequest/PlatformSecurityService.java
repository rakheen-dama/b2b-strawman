package io.b2mash.b2b.b2bstrawman.accessrequest;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import org.springframework.stereotype.Service;

/**
 * SpEL-accessible service for platform admin authorization checks. Intended for use with
 * {@code @PreAuthorize("@platformSecurityService.isPlatformAdmin()")}.
 */
@Service("platformSecurityService")
public class PlatformSecurityService {

  /** Returns true if the current request has the platform-admins group. */
  public boolean isPlatformAdmin() {
    return RequestScopes.isPlatformAdmin();
  }
}
