package io.b2mash.b2b.b2bstrawman.dev;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.MagicLinkService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Dev-only Thymeleaf controller for exercising the full portal flow. Profile-gated per ADR-033 --
 * never exposed in production. Bypasses CustomerAuthFilter (excluded via shouldNotFilter for
 * /portal/dev/**) and manages auth manually.
 */
@Controller
@Profile({"local", "dev"})
@RequestMapping("/portal/dev")
public class DevPortalController {

  private static final Logger log = LoggerFactory.getLogger(DevPortalController.class);

  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final PortalContactService portalContactService;
  private final PortalContactRepository portalContactRepository;
  private final CustomerRepository customerRepository;
  private final MagicLinkService magicLinkService;
  private final PortalJwtService portalJwtService;
  private final PortalReadModelService portalReadModelService;
  private final PortalReadModelRepository portalReadModelRepository;

  public DevPortalController(
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      PortalContactService portalContactService,
      PortalContactRepository portalContactRepository,
      CustomerRepository customerRepository,
      MagicLinkService magicLinkService,
      PortalJwtService portalJwtService,
      PortalReadModelService portalReadModelService,
      PortalReadModelRepository portalReadModelRepository) {
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.portalContactService = portalContactService;
    this.portalContactRepository = portalContactRepository;
    this.customerRepository = customerRepository;
    this.magicLinkService = magicLinkService;
    this.portalJwtService = portalJwtService;
    this.portalReadModelService = portalReadModelService;
    this.portalReadModelRepository = portalReadModelRepository;
  }

  /** Renders the magic link generation form with a dropdown of available organizations. */
  @GetMapping("/generate-link")
  public String showGenerateLinkForm(Model model) {
    model.addAttribute("orgs", orgSchemaMappingRepository.findAll());
    return "portal/generate-link";
  }

  /**
   * Generates a magic link for the given email and org. Finds an existing PortalContact or creates
   * one with GENERAL role. Displays the generated magic link URL on the form.
   */
  @PostMapping("/generate-link")
  public String generateLink(
      @RequestParam String email,
      @RequestParam String orgId,
      Model model,
      HttpServletRequest request) {
    model.addAttribute("orgs", orgSchemaMappingRepository.findAll());
    model.addAttribute("email", email);
    model.addAttribute("orgId", orgId);

    var mapping = orgSchemaMappingRepository.findByClerkOrgId(orgId).orElse(null);
    if (mapping == null) {
      model.addAttribute("error", "Organization not found: " + orgId);
      return "portal/generate-link";
    }

    String tenantSchema = mapping.getSchemaName();
    try {
      String magicLink =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .where(RequestScopes.ORG_ID, orgId)
              .call(
                  () -> {
                    // Try to find existing portal contact
                    var existingContact =
                        portalContactService.findByEmailAndOrg(email, orgId).orElse(null);

                    PortalContact contact;
                    if (existingContact != null) {
                      contact = existingContact;
                    } else {
                      // Find customer by email to create a contact
                      var customer = customerRepository.findByEmail(email).orElse(null);
                      if (customer == null) {
                        return null; // Signal: no customer found
                      }
                      contact =
                          portalContactService.createContact(
                              orgId,
                              customer.getId(),
                              email,
                              customer.getName(),
                              PortalContact.ContactRole.GENERAL);
                    }

                    String rawToken =
                        magicLinkService.generateToken(contact.getId(), request.getRemoteAddr());
                    return "/portal/dev/exchange?token=" + rawToken + "&orgId=" + orgId;
                  });

      if (magicLink == null) {
        model.addAttribute(
            "error", "No customer found with email '" + email + "' in this organization");
      } else {
        model.addAttribute("magicLink", magicLink);
      }
    } catch (Exception e) {
      log.warn("Error generating magic link for {} in {}: {}", email, orgId, e.getMessage());
      model.addAttribute("error", "Error generating magic link: " + e.getMessage());
    }

    return "portal/generate-link";
  }

  /**
   * Exchanges a raw magic link token for a portal JWT and redirects to the dashboard. This inlines
   * the exchange logic (same as PortalAuthController.exchangeToken) to avoid HTTP round-trips.
   */
  @GetMapping("/exchange")
  public String exchangeToken(@RequestParam String token, @RequestParam String orgId, Model model) {
    var mapping = orgSchemaMappingRepository.findByClerkOrgId(orgId).orElse(null);
    if (mapping == null) {
      model.addAttribute("orgs", orgSchemaMappingRepository.findAll());
      model.addAttribute("error", "Organization not found");
      return "portal/generate-link";
    }

    String tenantSchema = mapping.getSchemaName();
    try {
      // Verify, consume, and issue JWT in a single scoped block so that if JWT issuance
      // fails, the entire operation is within the same transactional/scoped context.
      String portalJwt =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .call(
                  () -> {
                    UUID portalContactId = magicLinkService.verifyAndConsumeToken(token);
                    var profile = portalReadModelService.getContactProfile(portalContactId);
                    return portalJwtService.issueToken(profile.customerId(), orgId);
                  });

      return "redirect:/portal/dev/dashboard?token=" + portalJwt;
    } catch (Exception e) {
      log.warn("Error exchanging magic link token: {}", e.getMessage());
      model.addAttribute("orgs", orgSchemaMappingRepository.findAll());
      model.addAttribute("error", "Invalid or expired magic link: " + e.getMessage());
      return "portal/generate-link";
    }
  }

  /** Renders the customer dashboard showing projects and documents. */
  @GetMapping("/dashboard")
  public String showDashboard(@RequestParam String token, Model model) {
    model.addAttribute("token", token);

    PortalJwtService.PortalClaims claims;
    try {
      claims = portalJwtService.verifyToken(token);
    } catch (Exception e) {
      model.addAttribute("error", "Invalid or expired portal token: " + e.getMessage());
      return "portal/dashboard";
    }

    var mapping = orgSchemaMappingRepository.findByClerkOrgId(claims.clerkOrgId()).orElse(null);
    if (mapping == null) {
      model.addAttribute("error", "Organization not found");
      return "portal/dashboard";
    }

    String orgId = claims.clerkOrgId();
    UUID customerId = claims.customerId();

    try {
      // Resolve portal contact to bind PORTAL_CONTACT_ID (matches CustomerAuthFilter behavior)
      var carrier =
          ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
              .where(RequestScopes.CUSTOMER_ID, customerId)
              .where(RequestScopes.ORG_ID, orgId);
      var contact =
          ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
              .call(
                  () ->
                      portalContactRepository
                          .findByCustomerIdAndOrgId(customerId, orgId)
                          .orElse(null));
      if (contact != null) {
        carrier = carrier.where(RequestScopes.PORTAL_CONTACT_ID, contact.getId());
      }

      var scopedCarrier = carrier;
      scopedCarrier.run(
          () -> {
            var projects = portalReadModelRepository.findProjectsByCustomer(orgId, customerId);
            var documents = portalReadModelRepository.findDocumentsByCustomer(orgId, customerId);
            model.addAttribute("projects", projects);
            model.addAttribute("documents", documents);
            model.addAttribute("orgId", orgId);
            model.addAttribute("customerId", customerId);
          });
    } catch (Exception e) {
      log.warn("Error loading dashboard: {}", e.getMessage());
      model.addAttribute("error", "Error loading dashboard: " + e.getMessage());
    }

    return "portal/dashboard";
  }

  /** Renders the project detail page with documents, comments, and summary. */
  @GetMapping("/project/{id}")
  public String showProjectDetail(@PathVariable UUID id, @RequestParam String token, Model model) {
    model.addAttribute("token", token);

    PortalJwtService.PortalClaims claims;
    try {
      claims = portalJwtService.verifyToken(token);
    } catch (Exception e) {
      model.addAttribute("error", "Invalid or expired portal token: " + e.getMessage());
      return "portal/project-detail";
    }

    var mapping = orgSchemaMappingRepository.findByClerkOrgId(claims.clerkOrgId()).orElse(null);
    if (mapping == null) {
      model.addAttribute("error", "Organization not found");
      return "portal/project-detail";
    }

    String orgId = claims.clerkOrgId();
    UUID customerId = claims.customerId();

    try {
      // Resolve portal contact to bind PORTAL_CONTACT_ID (matches CustomerAuthFilter behavior)
      var carrier =
          ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
              .where(RequestScopes.CUSTOMER_ID, customerId)
              .where(RequestScopes.ORG_ID, orgId);
      var contact =
          ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
              .call(
                  () ->
                      portalContactRepository
                          .findByCustomerIdAndOrgId(customerId, orgId)
                          .orElse(null));
      if (contact != null) {
        carrier = carrier.where(RequestScopes.PORTAL_CONTACT_ID, contact.getId());
      }

      var scopedCarrier = carrier;
      scopedCarrier.run(
          () -> {
            var project = portalReadModelService.getProjectDetail(id, customerId, orgId);
            model.addAttribute("project", project);

            var documents = portalReadModelRepository.findDocumentsByProject(id, orgId);
            model.addAttribute("documents", documents);

            var comments = portalReadModelService.listProjectComments(id, customerId, orgId);
            model.addAttribute("comments", comments);

            var summary =
                portalReadModelService.getProjectSummary(id, customerId, orgId).orElse(null);
            model.addAttribute(
                "totalHours", summary != null ? summary.totalHours() : BigDecimal.ZERO);
            model.addAttribute(
                "billableHours", summary != null ? summary.billableHours() : BigDecimal.ZERO);
            model.addAttribute("lastActivityAt", summary != null ? summary.lastActivityAt() : null);
          });
    } catch (Exception e) {
      log.warn("Error loading project detail for {}: {}", id, e.getMessage());
      model.addAttribute("error", "Error loading project: " + e.getMessage());
    }

    return "portal/project-detail";
  }
}
