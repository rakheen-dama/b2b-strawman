package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.portal.dto.PortalSessionContextDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated portal endpoint returning the aggregated session context (vertical profile, enabled
 * modules, terminology key, branding) for the currently authenticated portal contact.
 *
 * <p>Route is {@code /portal/session/context} -- it is handled by {@code CustomerAuthFilter} (which
 * only matches paths starting with {@code /portal/}) and resolves tenant / org identity from the
 * validated portal JWT via {@code RequestScopes}. No query parameters; the frontend should cache
 * the response for the lifetime of the SPA session.
 */
@RestController
@RequestMapping("/portal/session")
public class PortalContextController {

  private final PortalSessionContextService sessionContextService;

  public PortalContextController(PortalSessionContextService sessionContextService) {
    this.sessionContextService = sessionContextService;
  }

  @GetMapping("/context")
  public ResponseEntity<PortalSessionContextDto> getContext() {
    return ResponseEntity.ok(sessionContextService.resolve());
  }
}
