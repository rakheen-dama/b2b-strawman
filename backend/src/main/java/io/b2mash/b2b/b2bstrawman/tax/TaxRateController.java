package io.b2mash.b2b.b2bstrawman.tax;

import io.b2mash.b2b.b2bstrawman.tax.dto.CreateTaxRateRequest;
import io.b2mash.b2b.b2bstrawman.tax.dto.TaxRateResponse;
import io.b2mash.b2b.b2bstrawman.tax.dto.UpdateTaxRateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tax-rates")
public class TaxRateController {

  private final TaxRateService taxRateService;

  public TaxRateController(TaxRateService taxRateService) {
    this.taxRateService = taxRateService;
  }

  // All authenticated users (including members) can see inactive rates — needed for displaying
  // historical tax rate data on existing invoices and line items.
  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TaxRateResponse>> listTaxRates(
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return ResponseEntity.ok(taxRateService.listTaxRates(includeInactive));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaxRateResponse> createTaxRate(
      @Valid @RequestBody CreateTaxRateRequest request) {
    var response = taxRateService.createTaxRate(request);
    return ResponseEntity.created(URI.create("/api/tax-rates/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaxRateResponse> updateTaxRate(
      @PathVariable UUID id, @Valid @RequestBody UpdateTaxRateRequest request) {
    return ResponseEntity.ok(taxRateService.updateTaxRate(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deactivateTaxRate(@PathVariable UUID id) {
    taxRateService.deactivateTaxRate(id);
    return ResponseEntity.noContent().build();
  }
}
