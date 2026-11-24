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
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnPositionResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSheetNameResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSignOffResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSourceResponse;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnRowCommitters;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the take-on wire responses (no access checks — callers have made them). Never ships a
 * field nothing in 653A writes or reads (P-11, C-27).
 */
@Component
public class TakeOnBatchViews {

  /**
   * What the wizard may offer next (653A; ATTEST … DOWNLOAD_RESULT from 655A; the three trust
   * sign-off actions from 657C, R-5 — appended, the frontend union mirrors the order).
   */
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
    DOWNLOAD_RESULT,
    /** The caller may sign off this trust take-on (657C: owner-only capability + VIEW_TRUST). */
    SIGN_OFF,
    /** The caller is the live trust signer and may withdraw their own sign-off (657C). */
    WITHDRAW_SIGN_OFF,
    /** A live trust sign-off may be revoked by the preparer or an admin (D-113, 657C). */
    REVOKE_SIGN_OFF
  }

  /** Holding it (on top of the write access) is what lets a member attest (FD-37). */
  static final String ATTEST_CAPABILITY = "MANAGE_COMPLIANCE";

  /** Holding both is what lets a member sign off a trust take-on (FD-4, §17.9; 657C). */
  static final String SIGN_OFF_CAPABILITY = "SIGN_OFF_TRUST_TAKE_ON";

  static final String VIEW_TRUST_CAPABILITY = "VIEW_TRUST";

  /** The trust signer's own actions — what the sign-off review offers (657C). */
  private static final Set<Action> SIGNER_ACTIONS =
      EnumSet.of(Action.SIGN_OFF, Action.WITHDRAW_SIGN_OFF);

  /** Who is asking: what they may do beyond the batch's write access. */
  record Caller(boolean mayAttest, boolean maySignOff, UUID memberId) {

    static Caller current() {
      return new Caller(
          RequestScopes.hasCapability(ATTEST_CAPABILITY),
          RequestScopes.hasCapability(SIGN_OFF_CAPABILITY)
              && RequestScopes.hasCapability(VIEW_TRUST_CAPABILITY),
          RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null);
    }
  }

  /** The commit path of a dataset, as the claim sees it (R-2). */
  record CommitPath(boolean available, TakeOnSignOffKind requiredSignOff) {}

  private final TakeOnBatchRepository batches;
  private final TakeOnSourceRepository sources;
  private final TakeOnRowRepository rows;
  private final TakeOnSignOffRepository signOffs;
  private final OrganizationRepository organizations;
  private final TakeOnRowCommitters committers;
  private final TrustAccountRepository trustAccounts;
  private final TakeOnReportRepository reports;

  public TakeOnBatchViews(
      TakeOnBatchRepository batches,
      TakeOnSourceRepository sources,
      TakeOnRowRepository rows,
      TakeOnSignOffRepository signOffs,
      OrganizationRepository organizations,
      TakeOnRowCommitters committers,
      TrustAccountRepository trustAccounts,
      TakeOnReportRepository reports) {
    this.batches = batches;
    this.sources = sources;
    this.rows = rows;
    this.signOffs = signOffs;
    this.organizations = organizations;
    this.committers = committers;
    this.trustAccounts = trustAccounts;
    this.reports = reports;
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
    boolean trust = batch.getDataset() == TakeOnDataset.TRUST_POSITION;
    boolean redacted = redacted(batch);
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
                commitPath(batch.getDataset()),
                Caller.current(),
                redacted)
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
        validationSummary(batch),
        batch.isMidMonthAcknowledged(),
        position(batch),
        trust ? trustAccountName(batch.getTrustAccountId()) : null,
        trust && batch.getStatus() == TakeOnBatchStatus.COMPLETED
            ? reports.findByBatchId(batch.getId()).map(TakeOnReport::getId).orElse(null)
            : null);
  }

  /** The commit path of {@code dataset} (R-2): a row or whole-batch committer, and its sign-off. */
  CommitPath commitPath(TakeOnDataset dataset) {
    return new CommitPath(
        committers.canCommit(dataset), committers.requiredSignOff(dataset).orElse(null));
  }

  /** The trust account's name (null when the batch names none or it is gone). */
  String trustAccountName(UUID trustAccountId) {
    return trustAccountId == null
        ? null
        : trustAccounts.findById(trustAccountId).map(TrustAccount::getAccountName).orElse(null);
  }

  /** The trust take-on's reconciled position at cutover (657B); null on every other dataset. */
  static TakeOnPositionResponse position(TakeOnBatch batch) {
    if (batch.getDataset() != TakeOnDataset.TRUST_POSITION) {
      return null;
    }
    return new TakeOnPositionResponse(
        batch.getBankBalance(),
        batch.getOutstandingDepositsTotal(),
        batch.getOutstandingPaymentsTotal(),
        batch.getCashbookBalance(),
        batch.getFundLedgerBalance(),
        batch.getInvestedTotal());
  }

  /**
   * The dry run's stored summary, while it describes the batch: from validation on (VALIDATED,
   * COMMITTING, COMPLETED); null before and after a re-map (the batch then needs validating again).
   */
  static Map<String, Object> validationSummary(TakeOnBatch batch) {
    return switch (batch.getStatus()) {
      case VALIDATED, COMMITTING, COMPLETED -> batch.getSummary();
      default -> null;
    };
  }

  /**
   * The counts. While {@code COMMITTING} the commit outcome is read live from the rows (C-5: one
   * count query; the batch columns are written once, by {@code complete}).
   */
  TakeOnCountsResponse counts(TakeOnBatch batch) {
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

  /**
   * D-194: a validated trust batch with a row redacted since is offered neither sign-off nor commit
   * (the claim and the sign-off refuse it under the lock).
   */
  private boolean redacted(TakeOnBatch batch) {
    return batch.getDataset() == TakeOnDataset.TRUST_POSITION
        && batch.getStatus() == TakeOnBatchStatus.VALIDATED
        && rows.anyRedacted(batch.getId());
  }

  /**
   * The caller's {@code SIGN_OFF} / {@code WITHDRAW_SIGN_OFF} on this batch, exactly as {@link
   * #allowedActions} decides them for the batch view — the sign-off review (the wizard's step and
   * the Home sheet) reads them here, so the two cannot diverge.
   */
  List<Action> signerActions(TakeOnBatch batch) {
    return allowedActions(
            batch,
            sources.findByBatchIdOrderBySectionAsc(batch.getId()),
            signOffs.findByBatchIdAndRevokedAtIsNull(batch.getId()),
            commitPath(batch.getDataset()),
            Caller.current(),
            redacted(batch))
        .stream()
        .filter(SIGNER_ACTIONS::contains)
        .toList();
  }

  /** The firm's name (the organisation of the caller's org). */
  Optional<String> firmName() {
    return firmName(organizations);
  }

  /**
   * The firm's name, for a caller that cannot inject this component: {@link TakeOnReportService}
   * sits below it (this component → the batch committers → the trust commit → the report).
   */
  static Optional<String> firmName(OrganizationRepository organizations) {
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
   * COMMIT} when the dataset has a commit path (its slice has shipped), nothing blocks the batch
   * and — if the path requires a sign-off — a live one of that kind is on the current hash (exactly
   * what the claim, {@code TakeOnCommitService.requestCommit}, accepts). Clients only: {@code
   * ATTEST} (no live attestation, a valid row, no block, the caller holds {@code
   * MANAGE_COMPLIANCE}) or {@code REVOKE_ATTESTATION} (a live one). Trust only (657C, R-5): {@code
   * SIGN_OFF} (no live sign-off, nothing blocks it, no redacted row, the caller holds {@code
   * SIGN_OFF_TRUST_TAKE_ON} and {@code VIEW_TRUST}), {@code WITHDRAW_SIGN_OFF} (the caller is the
   * live signer and still holds both), {@code REVOKE_SIGN_OFF} (a live sign-off — the
   * preparer/admin path). A redacted row also withholds {@code COMMIT} (D-194). {@code COMMITTING}:
   * nothing. {@code COMPLETED}: the result report, and portal access for clients.
   */
  static List<Action> allowedActions(
      TakeOnBatch batch,
      List<TakeOnSource> batchSources,
      List<TakeOnSignOff> liveSignOffs,
      CommitPath commitPath,
      Caller caller,
      boolean redacted) {
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
        boolean blocked = batch.blocksCommit(); // a block; for a trust batch any error (D-191)
        if (commitPath.available()
            && !blocked
            && !redacted
            && signedOffForCommit(batch, liveSignOffs, commitPath.requiredSignOff())) {
          actions.add(Action.COMMIT);
        }
        if (batch.getDataset() == TakeOnDataset.CLIENTS) {
          boolean attested =
              liveSignOffs.stream()
                  .anyMatch(o -> o.getKind() == TakeOnSignOffKind.FICA_ATTESTATION);
          if (attested) {
            actions.add(Action.REVOKE_ATTESTATION);
          } else if (caller.mayAttest() && !blocked && batch.getValidRows() > 0) {
            actions.add(Action.ATTEST);
          }
        }
        if (batch.getDataset() == TakeOnDataset.TRUST_POSITION) {
          var signed =
              liveSignOffs.stream()
                  .filter(o -> o.getKind() == TakeOnSignOffKind.TRUST_SIGN_OFF)
                  .findFirst();
          if (signed.isEmpty()) {
            if (caller.maySignOff() && !blocked && !redacted) {
              actions.add(Action.SIGN_OFF);
            }
          } else {
            if (caller.maySignOff() && signed.get().getMemberId().equals(caller.memberId())) {
              actions.add(Action.WITHDRAW_SIGN_OFF);
            }
            actions.add(Action.REVOKE_SIGN_OFF);
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
      TakeOnBatch batch, List<TakeOnSignOff> liveSignOffs, TakeOnSignOffKind kind) {
    return kind == null
        || liveSignOffs.stream()
            .anyMatch(
                o ->
                    o.getKind() == kind && o.getValidationHash().equals(batch.getValidationHash()));
  }
}
