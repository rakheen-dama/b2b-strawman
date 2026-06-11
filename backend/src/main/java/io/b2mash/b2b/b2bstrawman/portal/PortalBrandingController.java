package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.portal.PortalBrandingService.BrandingResponse;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public branding endpoint for the customer portal. Returns org branding (name, logo URL, brand
 * color, footer text) without authentication, enabling the portal login page to display branded
 * content. Pure HTTP adapter — all data access lives in {@link PortalBrandingService}.
 */
@RestController
public class PortalBrandingController {

  private final PortalBrandingService brandingService;

  public PortalBrandingController(PortalBrandingService brandingService) {
    this.brandingService = brandingService;
  }

  @GetMapping("/portal/branding")
  public ResponseEntity<BrandingResponse> getBranding(@RequestParam String orgId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(brandingService.getBranding(orgId));
  }
}
