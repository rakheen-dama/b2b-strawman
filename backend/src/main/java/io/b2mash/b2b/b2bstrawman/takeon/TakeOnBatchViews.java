package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnAttestationStatementResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnCountsResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnMappingResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnParseOptionsResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSheetNameResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSignOffResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSourceResponse;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnRowCommitter;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnRowCommitters;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the take-on wire responses (no access checks — callers have made them). Never ships a
 * field nothing in 653A writes or reads (P-11, C-27).
 */
@Component
public class TakeOnBatchViews {

  /** What the wizard may offer next (653A; ATTEST … DOWNLOAD_RESULT from 655A; SIGN_OFF 657C). */
  public enum Action {
    ADD_SOURCE,
    REMOVE_SOURCE,
    MAP,
    VALIDATE,
    REVALIDATE,
    CANCEL,
    ATTEST,
    REVOKE_ATTESTATION,
    COMMIT,
    CREATE_PORTAL_ACCESS,
    DOWNLOAD_RESULT
  }

  /** Holding it (on top of the write access) is what lets a member attest (FD-37). */
  static final String ATTEST_CAPABILITY = "MANAGE_COMPLIANCE";

  private final TakeOnBatchRepository batches;
  private final TakeOnSourceRepository sources;
  private final TakeOnRowRepository rows;
  private final TakeOnSignOffRepository signOffs;
  private final OrganizationRepository organizations;
  private final TakeOnRowCommitters committers;

  public TakeOnBatchViews(
      TakeOnBatchRepository batches,
      TakeOnSourceRepository sources,
      TakeOnRowRepository rows,
      TakeOnSignOffRepository signOffs,
      OrganizationRepository organizations,
      TakeOnRowCommitters committers) {
    this.batches = batches;
    this.sources = sources;
    this.rows = rows;
    this.signOffs = signOffs;
    this.organizations = organizations;
    this.committers = committers;
  }

  @Transactional(readOnly = true)
  public TakeOnBatchResponse batch(UUID batchId) {
    return batch(
        batches
            .findById(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId)));
  }

  public TakeOnBatchResponse batch(TakeOnBatch batch) {
    var batchSources = sources.findByBatchIdOrderBySectionAsc(batch.getId());
    var live = signOffs.findByBatchIdAndRevokedAtIsNull(batch.getId());
    return new TakeOnBatchResponse(
        batch.getId(),
        batch.getDataset(),
        batch.getStatus(),
        batch.getName(),
        batch.getCutoverDate(),
        batch.getTrustAccountId(),
        batchSources.stream().map(TakeOnBatchViews::source).toList(),
        counts(batch),
        batch.getValidationHash(),
        batch.getValidatedAt(),
        allowedActions(
                batch,
                batchSources,
                live,
                committers.forDataset(batch.getDataset()).orElse(null),
                RequestScopes.hasCapability(ATTEST_CAPABILITY))
            .stream()
            .map(Enum::name)
            .toList(),
        batch.getCreatedAt(),
        batch.getCancelledAt(),
        batch.getCommitStartedAt(),
        batch.getCompletedAt(),
        batch.getLastError(),
        live.stream().map(TakeOnBatchViews::signOff).toList(),
        attestationStatement(batch),
        validationSummary(batch));
  }

  /**
   * The dry run's stored summary, while it describes the batch: from validation on (VALIDATED,
   * COMMITTING, COMPLETED); null before and after a re-map (the batch then needs validating again).
   */
  private static Map<String, Object> validationSummary(TakeOnBatch batch) {
    return switch (batch.getStatus()) {
      case VALIDATED, COMMITTING, COMPLETED -> batch.getSummary();
      default -> null;
    };
  }

  /**
   * The counts. While {@code COMMITTING} the commit outcome is read live from the rows (C-5: one
   * count query; the batch columns are written once, by {@code complete}).
   */
  private TakeOnCountsResponse counts(TakeOnBatch batch) {
    int created = batch.getCreatedRows();
    int skipped = batch.getSkippedRows();
    int failed = batch.getFailedRows();
    if (batch.getStatus() == TakeOnBatchStatus.COMMITTING) {
      var live = rows.commitCountsFor(batch.getId());
      created = Math.toIntExact(live.getCreated());
      skipped = Math.toIntExact(live.getSkipped());
      failed = Math.toIntExact(live.getFailed());
    }
    return new TakeOnCountsResponse(
        batch.getTotalRows(),
        batch.getValidRows(),
        batch.getErrorRows(),
        batch.getDuplicateRows(),
        batch.getBlockCount(),
        created,
        skipped,
        failed);
  }

  static TakeOnSignOffResponse signOff(TakeOnSignOff signOff) {
    return new TakeOnSignOffResponse(
        signOff.getId(),
        signOff.getKind(),
        signOff.getMemberName(),
        signOff.getRoleName(),
        signOff.getStatementVersion(),
        signOff.getSignedAt());
  }

  /**
   * The {@code FICA-ATTEST-v1} text with the firm name, exactly as an attestation would store it
   * (C-16) — for a validated clients batch only; null otherwise.
   */
  private TakeOnAttestationStatementResponse attestationStatement(TakeOnBatch batch) {
    if (batch.getDataset() != TakeOnDataset.CLIENTS
        || batch.getStatus() != TakeOnBatchStatus.VALIDATED) {
      return null;
    }
    return firmName()
        .map(
            firm ->
                new TakeOnAttestationStatementResponse(
                    TakeOnStatements.FICA_ATTEST_V1, TakeOnStatements.ficaAttestation(firm)))
        .orElse(null);
  }

  /** The firm's name (the organisation of the caller's org). */
  Optional<String> firmName() {
    String orgId = RequestScopes.getOrgIdOrNull();
    return orgId == null
        ? Optional.empty()
        : organizations.findByExternalOrgId(orgId).map(Organization::getName);
  }

  static TakeOnSourceResponse source(TakeOnSource source) {
    var mapping = TakeOnColumnMapping.fromMap(source.getColumnMapping());
    return new TakeOnSourceResponse(
        source.getId(),
        source.getSection(),
        source.getFileName(),
        source.getFileFormat().name(),
        source.getFileSize(),
        sheetNames(source.getSheetNames()),
        source.getSheetName(),
        source.getHeaderRow(),
        parseOptions(TakeOnParseOptions.fromMap(source.getParseOptions())),
        mapping == null
            ? null
            : new TakeOnMappingResponse(
                mapping.targets(), mapping.customFields(), mapping.parked(), mapping.defaults()),
        source.isMapped(),
        source.getRowCount(),
        source.getMalformedCount(),
        source.getBlankSkipped(),
        source.isPurged(),
        source.getUploadedAt());
  }

  static TakeOnParseOptionsResponse parseOptions(TakeOnParseOptions options) {
    return new TakeOnParseOptionsResponse(
        options.dateFormat(),
        options.decimalSeparator().name(),
        options.delimiter() == null ? null : String.valueOf(options.delimiter()),
        options.encoding());
  }

  static List<TakeOnSheetNameResponse> sheetNames(List<Map<String, Object>> stored) {
    if (stored == null) {
      return List.of();
    }
    return stored.stream()
        .map(
            m ->
                new TakeOnSheetNameResponse(
                    String.valueOf(m.get("name")), Boolean.TRUE.equals(m.get("hidden"))))
        .toList();
  }

  /**
   * What the wizard may offer (C-17). Open batches: the 653A actions; for a validated batch {@code
   * COMMIT} when the dataset has a row committer (its slice has shipped), nothing blocks the batch
   * and — if the committer requires a sign-off — a live one of that kind is on the current hash
   * (exactly what the claim, {@code TakeOnCommitService.requestCommit}, accepts). Clients only:
   * {@code ATTEST} (no live attestation, a valid row, no block, the caller holds {@code
   * MANAGE_COMPLIANCE}) or {@code REVOKE_ATTESTATION} (a live one). {@code COMMITTING}: nothing.
   * {@code COMPLETED}: the result report, and portal access for clients.
   */
  static List<Action> allowedActions(
      TakeOnBatch batch,
      List<TakeOnSource> batchSources,
      List<TakeOnSignOff> liveSignOffs,
      TakeOnRowCommitter<?> committer,
      boolean mayAttest) {
    if (batch.getStatus() == TakeOnBatchStatus.COMPLETED) {
      return batch.getDataset() == TakeOnDataset.CLIENTS
          ? List.of(Action.DOWNLOAD_RESULT, Action.CREATE_PORTAL_ACCESS)
          : List.of(Action.DOWNLOAD_RESULT);
    }
    if (!batch.getStatus().isOpen()) {
      return List.of();
    }
    var actions = new ArrayList<Action>();
    boolean sectionLeft =
        batch.getDataset().sections().stream()
            .anyMatch(s -> batchSources.stream().noneMatch(src -> src.getSection() == s));
    if (sectionLeft) {
      actions.add(Action.ADD_SOURCE);
    }
    if (!batchSources.isEmpty()) {
      actions.add(Action.REMOVE_SOURCE);
      actions.add(Action.MAP);
    }
    switch (batch.getStatus()) {
      case MAPPED -> actions.add(Action.VALIDATE);
      case VALIDATED -> {
        actions.add(Action.REVALIDATE);
        boolean blocked = batch.getBlockCount() > 0;
        if (committer != null && !blocked && signedOffForCommit(batch, liveSignOffs, committer)) {
          actions.add(Action.COMMIT);
        }
        if (batch.getDataset() == TakeOnDataset.CLIENTS) {
          boolean attested =
              liveSignOffs.stream()
                  .anyMatch(o -> o.getKind() == TakeOnSignOffKind.FICA_ATTESTATION);
          if (attested) {
            actions.add(Action.REVOKE_ATTESTATION);
          } else if (mayAttest && !blocked && batch.getValidRows() > 0) {
            actions.add(Action.ATTEST);
          }
        }
      }
      default -> {
        // UPLOADED: map first
      }
    }
    actions.add(Action.CANCEL);
    return List.copyOf(actions);
  }

  /** No sign-off required, or a live one of the required kind on the batch's current hash. */
  private static boolean signedOffForCommit(
      TakeOnBatch batch, List<TakeOnSignOff> liveSignOffs, TakeOnRowCommitter<?> committer) {
    var kind = committer.requiredSignOff();
    return kind == null
        || liveSignOffs.stream()
            .anyMatch(
                o ->
                    o.getKind() == kind && o.getValidationHash().equals(batch.getValidationHash()));
  }
}
