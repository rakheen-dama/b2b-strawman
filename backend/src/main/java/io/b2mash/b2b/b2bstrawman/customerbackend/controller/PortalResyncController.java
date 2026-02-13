package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalResyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/portal")
public class PortalResyncController {

  private static final Logger log = LoggerFactory.getLogger(PortalResyncController.class);

  private final PortalResyncService resyncService;

  public PortalResyncController(PortalResyncService resyncService) {
    this.resyncService = resyncService;
  }

  @PostMapping("/resync/{orgId}")
  public ResponseEntity<ResyncResponse> resync(@PathVariable String orgId) {
    log.info("Received portal resync request for org={}", orgId);
    var result = resyncService.resyncOrg(orgId);
    return ResponseEntity.ok(
        new ResyncResponse(
            "Resync completed for org " + orgId,
            result.projectsProjected(),
            result.documentsProjected()));
  }

  public record ResyncResponse(String message, int projectsProjected, int documentsProjected) {}
}
