package io.b2mash.b2b.b2bstrawman.integration.accounting;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages tax code mappings between Kazi tax modes and external accounting system tax codes. */
@Service
public class AccountingTaxCodeMappingService {

  private static final Logger LOG = LoggerFactory.getLogger(AccountingTaxCodeMappingService.class);

  private final AccountingTaxCodeMappingRepository repository;

  public AccountingTaxCodeMappingService(AccountingTaxCodeMappingRepository repository) {
    this.repository = repository;
  }

  /** Returns all tax code mappings for the given provider. */
  @Transactional(readOnly = true)
  public List<AccountingTaxCodeMapping> getByProvider(String providerId) {
    return repository.findByProviderId(providerId);
  }

  /** Updates the external tax code and display label on an existing mapping. */
  @Transactional
  public AccountingTaxCodeMapping update(UUID id, String externalTaxCode, String displayLabel) {
    var mapping =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AccountingTaxCodeMapping", id));
    mapping.setExternalTaxCode(externalTaxCode);
    mapping.setDisplayLabel(displayLabel);
    return repository.save(mapping);
  }

  /**
   * Deletes all mappings for the provider and re-inserts the ZA default tax code mappings (4 rows).
   */
  @Transactional
  public List<AccountingTaxCodeMapping> resetToDefaults(String providerId) {
    LOG.info("Resetting tax code mappings to ZA defaults for provider: {}", providerId);
    var existing = repository.findByProviderId(providerId);
    repository.deleteAll(existing);
    repository.flush();

    var defaults =
        List.of(
            new AccountingTaxCodeMapping(
                providerId, "STANDARD_15", "OUTPUT2", "Standard Rate (15%)", true),
            new AccountingTaxCodeMapping(
                providerId, "ZERO_RATED", "ZERORATEDOUTPUT", "Zero Rated Output", true),
            new AccountingTaxCodeMapping(
                providerId, "EXEMPT", "EXEMPTOUTPUT", "Exempt Output", true),
            new AccountingTaxCodeMapping(
                providerId, "OUT_OF_SCOPE", "NONE", "No Tax / Out of Scope", true));

    return repository.saveAll(defaults);
  }

  /** Resolves the external tax code for a given Kazi tax mode and provider. */
  @Transactional(readOnly = true)
  public AccountingTaxCodeMapping resolveForTaxMode(String providerId, String kaziTaxMode) {
    return repository
        .findByProviderIdAndKaziTaxMode(providerId, kaziTaxMode)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "AccountingTaxCodeMapping",
                    "providerId=" + providerId + ", kaziTaxMode=" + kaziTaxMode));
  }
}
