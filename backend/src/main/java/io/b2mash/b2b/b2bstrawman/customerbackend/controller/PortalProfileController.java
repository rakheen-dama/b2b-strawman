package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal profile endpoint. Returns the authenticated contact's profile including customer name. All
 * endpoints are read-only and require a valid portal JWT (enforced by CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal/me")
public class PortalProfileController {

  private final PortalReadModelService portalReadModelService;

  public PortalProfileController(PortalReadModelService portalReadModelService) {
    this.portalReadModelService = portalReadModelService;
  }

  /** Returns the authenticated contact's profile with customer details. */
  @GetMapping
  public ResponseEntity<PortalProfileResponse> getProfile() {
    UUID portalContactId = RequestScopes.requirePortalContactId();
    var profile = portalReadModelService.getContactProfile(portalContactId);

    return ResponseEntity.ok(
        new PortalProfileResponse(
            profile.contactId(),
            profile.customerId(),
            profile.customerName(),
            profile.email(),
            profile.displayName(),
            profile.role()));
  }

  public record PortalProfileResponse(
      UUID contactId,
      UUID customerId,
      String customerName,
      String email,
      String displayName,
      String role) {}
}
