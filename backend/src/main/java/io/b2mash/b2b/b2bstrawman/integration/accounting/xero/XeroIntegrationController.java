package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMapping;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMappingService;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.XeroOAuthService.XeroSyncSettings;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto.XeroConnectionResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Xero integration management: OAuth connect/disconnect, connection status, tax
 * code mappings, customer import, and sync settings. All endpoints delegate to service methods and
 * are gated by the {@code INTEGRATION_MANAGE} capability.
 */
@RestController
@RequestMapping("/api/integrations/xero")
public class XeroIntegrationController {

  private final XeroOAuthService xeroOAuthService;
  private final AccountingTaxCodeMappingService taxCodeMappingService;
  private final XeroCustomerImportService importService;

  public XeroIntegrationController(
      XeroOAuthService xeroOAuthService,
      AccountingTaxCodeMappingService taxCodeMappingService,
      XeroCustomerImportService importService) {
    this.xeroOAuthService = xeroOAuthService;
    this.taxCodeMappingService = taxCodeMappingService;
    this.importService = importService;
  }

  @GetMapping("/connect")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<XeroOAuthService.XeroConnectResult> connect() {
    return ResponseEntity.ok(xeroOAuthService.initiateConnect(RequestScopes.requireMemberId()));
  }

  @GetMapping("/callback")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<XeroOAuthService.XeroCallbackResult> callback(
      @RequestParam("code") String code, @RequestParam("state") String state) {
    return ResponseEntity.ok(
        xeroOAuthService.handleCallback(code, state, RequestScopes.requireMemberId()));
  }

  @GetMapping("/connection")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<XeroConnectionResponse> getConnection() {
    return xeroOAuthService
        .getActiveConnection()
        .map(c -> ResponseEntity.ok(XeroConnectionResponse.from(c)))
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/connection")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<Void> disconnect() {
    xeroOAuthService.disconnect(RequestScopes.requireMemberId());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/tax-mappings")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<List<AccountingTaxCodeMapping>> getTaxMappings() {
    return ResponseEntity.ok(taxCodeMappingService.getByProvider("xero"));
  }

  @PutMapping("/tax-mappings/{id}")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<AccountingTaxCodeMapping> updateTaxMapping(
      @PathVariable UUID id, @Valid @RequestBody UpdateTaxMappingRequest request) {
    return ResponseEntity.ok(
        taxCodeMappingService.update(id, request.externalTaxCode(), request.displayLabel()));
  }

  @PostMapping("/tax-mappings/reset")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<List<AccountingTaxCodeMapping>> resetTaxMappings() {
    return ResponseEntity.ok(taxCodeMappingService.resetToDefaults("xero"));
  }

  @GetMapping("/tax-rates")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<Map<String, Object>> getTaxRates() {
    return ResponseEntity.ok(xeroOAuthService.getXeroTaxRates());
  }

  @PostMapping("/import-customers")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<CustomerImportSummary> importCustomers() {
    return ResponseEntity.ok(
        importService.importCustomersFromConnectedOrg(RequestScopes.requireMemberId()));
  }

  @GetMapping("/settings")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<XeroSyncSettings> getSettings() {
    return ResponseEntity.ok(xeroOAuthService.getSettings());
  }

  @PutMapping("/settings")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<XeroSyncSettings> updateSettings(
      @Valid @RequestBody XeroSyncSettings settings) {
    return ResponseEntity.ok(xeroOAuthService.updateSettings(settings));
  }

  // --- Request DTOs ---

  public record UpdateTaxMappingRequest(
      @NotBlank(message = "externalTaxCode must not be blank") String externalTaxCode,
      @NotBlank(message = "displayLabel must not be blank") String displayLabel) {}
}
