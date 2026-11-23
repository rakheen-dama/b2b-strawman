package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnPortalContactsResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnTemplateFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * After a commit (Phase 93 655A, §17.3.3): the per-row result report and the explicit "Create
 * portal access" action (FD-10). Kept apart from {@link TakeOnBatchService} so that service stays
 * small (D-27 precedent).
 */
@Service
public class TakeOnResultService {

  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
  private static final Pattern PLAIN_NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");
  private static final List<String> HEADERS =
      List.of("Row", "Previous system ID", "Status", "Kazi record ID", "Message");

  /** Clients whose name and email are gone: no portal contact is created for them. */
  private static final Set<LifecycleStatus> REMOVED =
      Set.of(LifecycleStatus.ANONYMIZED, LifecycleStatus.OFFBOARDED);

  /** The row statuses as the wizard shows them (§17.10 X3) — never the raw enum. */
  static final Map<TakeOnRowStatus, String> STATUS_LABELS =
      Map.of(
          TakeOnRowStatus.PENDING, "Pending",
          TakeOnRowStatus.VALIDATED, "Valid",
          TakeOnRowStatus.ERROR, "Error",
          TakeOnRowStatus.CREATED, "Created",
          TakeOnRowStatus.SKIPPED_DUPLICATE, "Duplicate — skipped",
          TakeOnRowStatus.SKIPPED_ZERO, "Nil balance — skipped",
          TakeOnRowStatus.FAILED, "Failed");

  private final TakeOnBatchRepository batches;
  private final TakeOnRowRepository rows;
  private final TakeOnAccessPolicy accessPolicy;
  private final CustomerRepository customers;
  private final PortalContactRepository portalContacts;
  private final PortalContactService portalContactService;
  private final TakeOnBatchViews views;
  private final TakeOnAudit audit;

  public TakeOnResultService(
      TakeOnBatchRepository batches,
      TakeOnRowRepository rows,
      TakeOnAccessPolicy accessPolicy,
      CustomerRepository customers,
      PortalContactRepository portalContacts,
      PortalContactService portalContactService,
      TakeOnBatchViews views,
      TakeOnAudit audit) {
    this.batches = batches;
    this.rows = rows;
    this.accessPolicy = accessPolicy;
    this.customers = customers;
    this.portalContacts = portalContacts;
    this.portalContactService = portalContactService;
    this.views = views;
    this.audit = audit;
  }

  /**
   * {@code GET …/result.csv}: one line per row — row number, previous-system id, status (X3 label),
   * the Kazi record created or matched, and the message. Built in memory (≤ 10 000 rows) in the
   * request's own scope, not streamed: a streaming body runs on another thread without the tenant
   * bound (C-22). Formula injection is defused.
   */
  @Transactional(readOnly = true)
  public TakeOnTemplateFile resultCsv(UUID batchId) {
    var batch =
        batches
            .findById(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId));
    accessPolicy.requireRead(batch.getDataset());
    if (batch.getStatus() != TakeOnBatchStatus.COMPLETED) {
      throw TakeOnProblems.noResultYet();
    }
    var lines = new StringBuilder(String.join(",", HEADERS)).append("\r\n");
    for (TakeOnRow row : rows.findByBatchIdOrderBySourceIdAscRowNumberAsc(batchId)) {
      lines
          .append(
              List.of(
                      String.valueOf(row.getRowNumber()),
                      nullToEmpty(row.getSourceRef()),
                      STATUS_LABELS.get(row.getStatus()),
                      row.getResultEntityId() == null ? "" : row.getResultEntityId().toString(),
                      message(row))
                  .stream()
                  .map(TakeOnResultService::csv)
                  .collect(Collectors.joining(",")))
          .append("\r\n");
    }
    byte[] body = lines.toString().getBytes(StandardCharsets.UTF_8);
    var out = new ByteArrayOutputStream(body.length + UTF8_BOM.length);
    out.writeBytes(UTF8_BOM);
    out.writeBytes(body);
    return new TakeOnTemplateFile(out.toByteArray(), "take-on-result-" + batchId + ".csv");
  }

  /**
   * {@code POST …/portal-contacts}: a {@code GENERAL} portal contact for each client the batch
   * created that has none yet, through {@link PortalContactService#createContact} — which sends no
   * email (FD-10; magic-link invitations stay the separate per-client action). Idempotent: a second
   * call creates nothing. The batch row is locked so two clicks cannot both create. A client that
   * has been anonymised ({@code ANONYMIZED}) or offboarded ({@code OFFBOARDED} — a data-deletion
   * request leaves it so, with its email replaced) is skipped.
   */
  @Transactional
  public TakeOnPortalContactsResponse createPortalContacts(UUID batchId) {
    var batch =
        batches
            .findByIdForUpdate(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId));
    accessPolicy.requireWrite(batch.getDataset()); // CUSTOMER_MANAGEMENT for clients
    if (batch.getDataset() != TakeOnDataset.CLIENTS
        || batch.getStatus() != TakeOnBatchStatus.COMPLETED) {
      throw TakeOnProblems.notCompleted();
    }
    String orgId = RequestScopes.requireOrgId();
    int created = 0;
    int skipped = 0;
    for (TakeOnRow row :
        rows.findByBatchIdAndStatusOrderByRowNumberAsc(batchId, TakeOnRowStatus.CREATED)) {
      // Locked, then read: the one guard shared with both anonymisation paths, which lock the
      // client first — so the contact gets the client's current name and email, never ones an
      // anonymisation has removed, and an anonymisation waits for the contact and then finds it.
      var client = customers.findByIdForUpdate(row.getResultEntityId()).orElse(null);
      if (client == null
          || REMOVED.contains(client.getLifecycleStatus())
          || !portalContacts.findByCustomerId(client.getId()).isEmpty()) {
        skipped++;
        continue;
      }
      portalContactService.createContact(
          orgId,
          client.getId(),
          client.getEmail(),
          client.getName(),
          PortalContact.ContactRole.GENERAL);
      created++;
    }
    audit.portalContactsCreated(batch, created, skipped);
    return new TakeOnPortalContactsResponse(views.batch(batch), created, skipped);
  }

  private static String message(TakeOnRow row) {
    return switch (row.getStatus()) {
      case FAILED -> nullToEmpty(row.getFailureReason());
      case ERROR ->
          row.getErrors().stream()
              .map(e -> e.get("message"))
              .filter(m -> m != null)
              .map(Object::toString)
              .collect(Collectors.joining("; "));
      default -> "";
    };
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  /** CSV-quotes a cell and defuses formula injection (the {@code ReportRenderingService} rule). */
  static String csv(String value) {
    if (value == null) {
      return "";
    }
    if (!value.isEmpty()
        && "=+-@\t\r".indexOf(value.charAt(0)) >= 0
        && !PLAIN_NUMBER.matcher(value).matches()) {
      value = "'" + value;
    }
    if (value.contains(",")
        || value.contains("\"")
        || value.contains("\n")
        || value.contains("\r")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
