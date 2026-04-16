package io.b2mash.b2b.b2bstrawman.packs;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for pack catalog browsing, installation, and uninstallation. */
@RestController
@RequestMapping("/api/packs")
public class PackCatalogController {

  private final PackCatalogService packCatalogService;
  private final PackInstallService packInstallService;

  public PackCatalogController(
      PackCatalogService packCatalogService, PackInstallService packInstallService) {
    this.packCatalogService = packCatalogService;
    this.packInstallService = packInstallService;
  }

  /** Returns the full pack catalog, optionally filtered by the tenant's vertical profile. */
  @GetMapping("/catalog")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<List<PackCatalogEntry>> listCatalog(
      @RequestParam(defaultValue = "false") boolean all) {
    return ResponseEntity.ok(packCatalogService.listCatalog(all));
  }

  /** Returns all installed packs for the current tenant. */
  @GetMapping("/installed")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<List<PackCatalogEntry>> listInstalled() {
    return ResponseEntity.ok(packCatalogService.listInstalled());
  }

  /** Checks whether a pack can be safely uninstalled. */
  @GetMapping("/{packId}/uninstall-check")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<UninstallCheck> checkUninstallable(@PathVariable String packId) {
    return ResponseEntity.ok(packInstallService.checkUninstallable(packId));
  }

  /** Installs a pack for the current tenant. */
  @PostMapping("/{packId}/install")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<PackInstallResponse> install(@PathVariable String packId) {
    return ResponseEntity.ok(
        PackInstallResponse.from(
            packInstallService.install(packId, RequestScopes.requireMemberId().toString())));
  }

  /** Uninstalls a pack for the current tenant. */
  @DeleteMapping("/{packId}")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Void> uninstall(@PathVariable String packId) {
    packInstallService.uninstall(packId, RequestScopes.requireMemberId().toString());
    return ResponseEntity.noContent().build();
  }
}
