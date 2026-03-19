package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingActivityService {

  private static final Logger log = LoggerFactory.getLogger(ProcessingActivityService.class);

  private final ProcessingActivityRepository repository;

  public ProcessingActivityService(ProcessingActivityRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public Page<ProcessingActivity> list(Pageable pageable) {
    return repository.findAll(pageable);
  }

  @Transactional
  public ProcessingActivity create(ProcessingActivityController.ProcessingActivityRequest request) {
    var activity =
        new ProcessingActivity(
            request.category(),
            request.description(),
            request.legalBasis(),
            request.dataSubjects(),
            request.retentionPeriod(),
            request.recipients());
    return repository.save(activity);
  }

  @Transactional
  public ProcessingActivity update(
      UUID id, ProcessingActivityController.ProcessingActivityRequest request) {
    var activity =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProcessingActivity", id));
    activity.update(
        request.category(),
        request.description(),
        request.legalBasis(),
        request.dataSubjects(),
        request.retentionPeriod(),
        request.recipients());
    return repository.save(activity);
  }

  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("ProcessingActivity", id);
    }
    repository.deleteById(id);
  }

  /**
   * Seeds default processing activity entries for the given jurisdiction. Idempotent -- checks by
   * category before each create. Silently skips non-ZA jurisdictions.
   */
  @Transactional
  public void seedJurisdictionDefaults(String jurisdiction) {
    if (!"ZA".equals(jurisdiction)) {
      return;
    }
    seedIfAbsent(
        "Client Information",
        "Names, contact details, tax numbers for client relationship management",
        "contractual_necessity",
        "Clients",
        "Duration of engagement + 5 years",
        "None");
    seedIfAbsent(
        "Financial Records",
        "Invoices, payment records, billing information",
        "legal_obligation",
        "Clients",
        "5 years from end of tax year",
        "SARS (legal obligation)");
    seedIfAbsent(
        "Time & Work Records",
        "Time entries, task descriptions linked to client work",
        "legitimate_interest",
        "Clients, Employees",
        "Duration of engagement + 5 years",
        "None");
    seedIfAbsent(
        "Project Documentation",
        "Documents, proposals, correspondence related to engagements",
        "contractual_necessity",
        "Clients",
        "Duration of engagement + 5 years",
        "None");
    seedIfAbsent(
        "Communication Records",
        "Comments, notifications, activity feeds",
        "legitimate_interest",
        "Clients, Employees",
        "3 years",
        "None");
    seedIfAbsent(
        "Portal Access",
        "Magic link tokens, access logs for client portal",
        "contractual_necessity",
        "Client contacts",
        "Duration of portal access + 1 year",
        "None");
    log.info("Seeded ZA jurisdiction defaults for processing activities");
  }

  private void seedIfAbsent(
      String category,
      String description,
      String legalBasis,
      String dataSubjects,
      String retentionPeriod,
      String recipients) {
    if (!repository.existsByCategory(category)) {
      repository.save(
          new ProcessingActivity(
              category, description, legalBasis, dataSubjects, retentionPeriod, recipients));
    }
  }
}
