package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.CapabilityAuthorizationService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnAttestationRequest;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnCountsResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnPositionResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnRowResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSignOffResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The FICA attestation of a clients batch (Phase 93 655A, §17.3.3, FD-5, FD-37), the revoke of a
 * live attestation or sign-off before commit, and (657C, §17.3.9, ADR-391, FD-4, FD-36) the trust
 * half: the sign-off queue, the review, the partner/director sign-off and the signer's own
 * withdrawal.
 *
 * <p>Attest: {@code MANAGE_TAKE_ON} (annotation) + {@code CUSTOMER_MANAGEMENT} (access policy) +
 * {@code MANAGE_COMPLIANCE} (here). The batch row is locked {@code FOR UPDATE} before any check
 * (D-33), so attestation serialises with re-validation and the commit claim (X5).
 *
 * <p>Trust sign-off: the four signer endpoints are {@code SIGN_OFF_TRUST_TAKE_ON} (annotation —
 * owner-only, grantable; admin does not inherit it) + {@code VIEW_TRUST} (here, first), and never
 * {@code MANAGE_TAKE_ON}: a custom "Partner" role holding only those two reads what it signs from
 * the review alone. The sign-off locks the batch {@code FOR UPDATE} (as validation and the claim
 * do) and refuses — under that lock — a batch that is not a validated trust batch, that cannot be
 * committed ({@code blocksCommit()}, D-191), that holds a redacted row (D-194), or whose hash is
 * stale.
 */
@Service
public class TakeOnSignOffService {

  static final String COMPLIANCE_CAPABILITY = "MANAGE_COMPLIANCE";

  static final String VIEW_TRUST = "VIEW_TRUST";

  static final int MAX_PAGE_SIZE = 200;

  // --- 657C wire records (the signer's sheet reads only these) ---

  /** One trust take-on awaiting a sign-off ({@code GET /api/take-on/sign-off-queue}). */
  public record TakeOnSignOffQueueItem(
      UUID batchId,
      UUID trustAccountId,
      String trustAccountName,
      LocalDate cutoverDate,
      String preparedByName,
      Instant validatedAt) {}

  /**
   * The {@code TAKEON-SIGNOFF-v1} declaration with the firm, account and cutover date filled in.
   */
  public record TakeOnSignOffDeclarationResponse(String version, String text) {}

  /** {@code POST …/sign-off}. */
  public record TakeOnSignOffRequest(
      String validationHash, String statementVersion, Boolean declarationAccepted) {}

  /**
   * What the signer signs ({@code GET …/sign-off-review}, and the answer of the sign-off and the
   * withdrawal): the batch's position inputs, counts and equation summary, the declaration, the
   * live sign-off, whether the caller is its signer, the signer actions ({@code SIGN_OFF}, {@code
   * WITHDRAW_SIGN_OFF}) and one page of the rows ({@code PagedModel}: {@code {content, page}}).
   */
  public record TakeOnSignOffReviewResponse(
      UUID batchId,
      TakeOnBatchStatus status,
      UUID trustAccountId,
      String trustAccountName,
      LocalDate cutoverDate,
      boolean midMonthAcknowledged,
      TakeOnPositionResponse position,
      TakeOnCountsResponse counts,
      Map<String, Object> summary,
      String validationHash,
      Instant validatedAt,
      String preparedByName,
      TakeOnSignOffDeclarationResponse declaration,
      TakeOnSignOffResponse liveSignOff,
      boolean signedByCaller,
      List<String> allowedActions,
      PagedModel<TakeOnRowResponse> rows) {}

  private final TakeOnBatchRepository batches;
  private final TakeOnSourceRepository sources;
  private final TakeOnSignOffRepository signOffs;
  private final TakeOnAccessPolicy accessPolicy;
  private final CapabilityAuthorizationService capabilities;
  private final MemberRepository members;
  private final TakeOnBatchViews views;
  private final TakeOnAudit audit;
  private final TakeOnRowRepository rows;
  private final MemberNameResolver memberNames;

  public TakeOnSignOffService(
      TakeOnBatchRepository batches,
      TakeOnSourceRepository sources,
      TakeOnSignOffRepository signOffs,
      TakeOnAccessPolicy accessPolicy,
      CapabilityAuthorizationService capabilities,
      MemberRepository members,
      TakeOnBatchViews views,
      TakeOnAudit audit,
      TakeOnRowRepository rows,
      MemberNameResolver memberNames) {
    this.batches = batches;
    this.sources = sources;
    this.signOffs = signOffs;
    this.accessPolicy = accessPolicy;
    this.capabilities = capabilities;
    this.members = members;
    this.views = views;
    this.audit = audit;
    this.rows = rows;
    this.memberNames = memberNames;
  }

  /** {@code POST …/attestation}: one live {@code FICA_ATTESTATION} per validated clients batch. */
  @Transactional
  public TakeOnBatchResponse attest(UUID batchId, TakeOnAttestationRequest request) {
    var batch = lock(batchId);
    accessPolicy.requireWrite(batch.getDataset()); // CUSTOMER_MANAGEMENT for clients
    capabilities.requireCapability(COMPLIANCE_CAPABILITY); // FD-37
    if (batch.getDataset() != TakeOnDataset.CLIENTS) {
      throw TakeOnProblems.wrongState("Only a clients take-on is attested.");
    }
    if (batch.getStatus() != TakeOnBatchStatus.VALIDATED) {
      throw TakeOnProblems.wrongState("Only a validated take-on batch can be attested.");
    }
    if (batch.getBlockCount() > 0) {
      throw TakeOnProblems.blocked(); // no sign-off on a batch that cannot be committed
    }
    if (request == null
        || request.validationHash() == null
        || !request.validationHash().equals(batch.getValidationHash())) {
      throw TakeOnProblems.hashMismatch();
    }
    if (!TakeOnStatements.FICA_ATTEST_V1.equals(request.statementVersion())) {
      throw new InvalidStateException(
          "Statement version not accepted",
          "Attest with the current statement (" + TakeOnStatements.FICA_ATTEST_V1 + ").");
    }
    var defaults = mappingDefaults(batchId);
    var requested = request.defaults() == null ? Map.<String, String>of() : request.defaults();
    if (!defaults.equals(requested)) {
      // C-8: the batch defaults are the mapping step's (covered by the hash); the attestation only
      // records them as evidence, so it must show exactly those.
      throw new InvalidStateException(
          "Batch defaults changed",
          "The batch defaults shown are not the ones the batch was validated with. Reload the"
              + " take-on and attest again.");
    }
    // Something to commit: valid rows, duplicates included (a duplicate's commit records its
    // previous-system id against the existing client). row_count = the clients it would create.
    if (batch.getValidRows() <= 0) {
      throw TakeOnProblems.nothingToAttest();
    }
    int rowCount = Math.max(0, batch.getValidRows() - batch.getDuplicateRows());
    if (signOffs
        .findByBatchIdAndKindAndRevokedAtIsNull(batchId, TakeOnSignOffKind.FICA_ATTESTATION)
        .isPresent()) {
      throw TakeOnProblems.alreadyAttested();
    }
    String firm =
        views
            .firmName()
            .orElseThrow(() -> new IllegalStateException("The firm has no organisation record."));
    var signOff =
        record(
            batch,
            TakeOnSignOffKind.FICA_ATTESTATION,
            TakeOnStatements.FICA_ATTEST_V1,
            TakeOnStatements.ficaAttestation(firm),
            defaults.isEmpty() ? null : new LinkedHashMap<>(defaults),
            TakeOnProblems::alreadyAttested);
    audit.batchAttested(batch, signOff, rowCount);
    return views.batch(batch);
  }

  /**
   * {@code DELETE …/sign-offs/{signOffId}}: withdraws a live attestation (or, from 657C, a trust
   * sign-off) before commit. Write access to the dataset; the batch must still be {@code
   * VALIDATED}.
   */
  @Transactional
  public TakeOnBatchResponse revoke(UUID batchId, UUID signOffId) {
    var batch = lock(batchId);
    accessPolicy.requireWrite(batch.getDataset());
    var signOff =
        signOffs
            .findByIdAndBatchId(signOffId, batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on sign-off", signOffId));
    if (batch.getStatus() != TakeOnBatchStatus.VALIDATED) {
      throw TakeOnProblems.wrongState(
          "An attestation can be withdrawn only before the take-on is committed.");
    }
    try {
      signOff.revoke(TakeOnSignOff.RevokedReason.REVOKED);
    } catch (IllegalStateException e) {
      throw TakeOnProblems.wrongState(e.getMessage());
    }
    audit.signOffRevoked(batch, signOff);
    return views.batch(batch);
  }

  /**
   * The live sign-off a commit of {@code batch} requires ({@code kind}), else 409 {@code
   * TAKE_ON_ATTESTATION_REQUIRED} (a trust batch: {@code TAKE_ON_SIGN_OFF_REQUIRED}, 657C); for a
   * dataset that needs none, {@link TakeOnSignOff#notRequired}. Called by the claim under the batch
   * lock.
   */
  TakeOnSignOff requireLiveFor(TakeOnBatch batch, TakeOnSignOffKind kind) {
    if (kind == null) {
      return TakeOnSignOff.notRequired(Objects.requireNonNull(batch.getValidationHash()));
    }
    return signOffs
        .findByBatchIdAndKindAndRevokedAtIsNull(batch.getId(), kind)
        .orElseThrow(
            kind == TakeOnSignOffKind.TRUST_SIGN_OFF
                ? TakeOnProblems::signOffRequired
                : TakeOnProblems::attestationRequired);
  }

  // --- 657C: the trust sign-off ---

  /**
   * {@code GET /api/take-on/sign-off-queue}: validated trust take-ons that can be committed (no
   * block, no error — D-191), hold no redacted row (D-194) and have no live sign-off; oldest
   * validation first.
   */
  @Transactional(readOnly = true)
  public List<TakeOnSignOffQueueItem> signOffQueue() {
    capabilities.requireCapability(VIEW_TRUST);
    var waiting =
        batches
            .findByDatasetAndStatusOrderByValidatedAtAsc(
                TakeOnDataset.TRUST_POSITION, TakeOnBatchStatus.VALIDATED)
            .stream()
            .filter(b -> !b.blocksCommit())
            .filter(
                b ->
                    signOffs
                        .findByBatchIdAndKindAndRevokedAtIsNull(
                            b.getId(), TakeOnSignOffKind.TRUST_SIGN_OFF)
                        .isEmpty())
            .filter(b -> !rows.anyRedacted(b.getId()))
            .toList();
    var preparers =
        memberNames.resolveNames(waiting.stream().map(TakeOnBatch::getCreatedBy).toList());
    return waiting.stream()
        .map(
            b ->
                new TakeOnSignOffQueueItem(
                    b.getId(),
                    b.getTrustAccountId(),
                    views.trustAccountName(b.getTrustAccountId()),
                    b.getCutoverDate(),
                    preparers.get(b.getCreatedBy()),
                    b.getValidatedAt()))
        .toList();
  }

  /** {@code GET …/sign-off-review?page=}: what the signer signs (trust batches only). */
  @Transactional(readOnly = true)
  public TakeOnSignOffReviewResponse signOffReview(UUID batchId, Pageable pageable) {
    capabilities.requireCapability(VIEW_TRUST);
    var batch =
        batches
            .findById(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId));
    requireTrust(batch);
    return review(batch, pageable);
  }

  /**
   * {@code POST …/sign-off}: one live {@code TRUST_SIGN_OFF} per validated trust batch, bound to
   * its hash, with the {@code TAKEON-SIGNOFF-v1} declaration as accepted (firm, account and cutover
   * date filled in) and the signer's name and role. Audits {@code takeon.batch.signed_off}.
   */
  @Transactional
  public TakeOnSignOffReviewResponse signOff(UUID batchId, TakeOnSignOffRequest request) {
    capabilities.requireCapability(VIEW_TRUST);
    var batch = lock(batchId);
    requireTrust(batch);
    if (batch.getStatus() != TakeOnBatchStatus.VALIDATED) {
      throw TakeOnProblems.wrongState("Only a validated trust take-on can be signed off.");
    }
    if (batch.getBlockCount() > 0) {
      throw TakeOnProblems.blocked();
    }
    if (batch.blocksCommit()) {
      throw TakeOnProblems.errorsRemain(); // committed whole: any error stops it (D-191)
    }
    if (rows.anyRedacted(batchId)) {
      throw TakeOnProblems.rowsRedacted(); // D-194, under the lock
    }
    if (request == null
        || request.validationHash() == null
        || !request.validationHash().equals(batch.getValidationHash())) {
      throw TakeOnProblems.hashMismatch();
    }
    if (!TakeOnStatements.TAKEON_SIGNOFF_V1.equals(request.statementVersion())) {
      throw new InvalidStateException(
          "Statement version not accepted",
          "Sign off with the current declaration (" + TakeOnStatements.TAKEON_SIGNOFF_V1 + ").");
    }
    if (!Boolean.TRUE.equals(request.declarationAccepted())) {
      throw TakeOnProblems.declarationRequired();
    }
    if (signOffs
        .findByBatchIdAndKindAndRevokedAtIsNull(batchId, TakeOnSignOffKind.TRUST_SIGN_OFF)
        .isPresent()) {
      throw TakeOnProblems.alreadySignedOff();
    }
    String statement =
        declaration(batch)
            .map(TakeOnSignOffDeclarationResponse::text)
            .orElseThrow(() -> new IllegalStateException("The firm has no organisation record."));
    var signOff =
        record(
            batch,
            TakeOnSignOffKind.TRUST_SIGN_OFF,
            TakeOnStatements.TAKEON_SIGNOFF_V1,
            statement,
            null,
            TakeOnProblems::alreadySignedOff);
    audit.batchSignedOff(batch, signOff);
    return review(batch, Pageable.unpaged());
  }

  /**
   * {@code DELETE …/trust-sign-off}: the live signer withdraws their own sign-off before commit
   * (reason {@code REVOKED}); anyone else 403. Audits {@code takeon.batch.sign_off_revoked}.
   */
  @Transactional
  public TakeOnSignOffReviewResponse withdrawMySignOff(UUID batchId) {
    capabilities.requireCapability(VIEW_TRUST);
    var batch = lock(batchId);
    requireTrust(batch);
    var signOff =
        signOffs
            .findByBatchIdAndKindAndRevokedAtIsNull(batchId, TakeOnSignOffKind.TRUST_SIGN_OFF)
            .orElseThrow(
                () -> TakeOnProblems.wrongState("This trust take-on has no live sign-off."));
    if (!signOff.getMemberId().equals(RequestScopes.requireMemberId())) {
      throw new ForbiddenException(
          "Access denied", "Only the member who signed off can withdraw this sign-off.");
    }
    if (batch.getStatus() != TakeOnBatchStatus.VALIDATED) {
      throw TakeOnProblems.wrongState(
          "A sign-off can be withdrawn only before the take-on is committed.");
    }
    signOff.revoke(TakeOnSignOff.RevokedReason.REVOKED);
    audit.signOffRevoked(batch, signOff);
    return review(batch, Pageable.unpaged());
  }

  /**
   * Records the caller's live sign-off of {@code kind} on the batch's current hash, with their name
   * and role as they are now. A concurrent second live one trips {@code uq_take_on_sign_offs_live}
   * and becomes {@code conflict} (409).
   */
  private TakeOnSignOff record(
      TakeOnBatch batch,
      TakeOnSignOffKind kind,
      String statementVersion,
      String statement,
      Map<String, Object> defaults,
      Supplier<? extends RuntimeException> conflict) {
    UUID memberId = RequestScopes.requireMemberId();
    var member =
        members
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
    String roleName =
        member.getOrgRoleEntity() == null ? "Member" : member.getOrgRoleEntity().getName();
    var signOff =
        new TakeOnSignOff(
            batch.getId(),
            kind,
            memberId,
            member.getName(),
            roleName,
            statementVersion,
            statement,
            batch.getValidationHash(),
            defaults);
    try {
      signOffs.saveAndFlush(signOff);
    } catch (DataIntegrityViolationException e) {
      throw conflict.get();
    }
    return signOff;
  }

  private static void requireTrust(TakeOnBatch batch) {
    if (batch.getDataset() != TakeOnDataset.TRUST_POSITION) {
      throw TakeOnProblems.wrongState("Only a trust take-on is signed off.");
    }
  }

  private TakeOnSignOffReviewResponse review(TakeOnBatch batch, Pageable pageable) {
    var live =
        signOffs.findByBatchIdAndKindAndRevokedAtIsNull(
            batch.getId(), TakeOnSignOffKind.TRUST_SIGN_OFF);
    UUID caller = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    boolean signedByCaller = live.isPresent() && live.get().getMemberId().equals(caller);
    var actions = views.signerActions(batch).stream().map(Enum::name).toList();
    int number = pageable.isPaged() ? pageable.getPageNumber() : 0;
    int size =
        pageable.isPaged() ? Math.max(1, Math.min(MAX_PAGE_SIZE, pageable.getPageSize())) : 50;
    var page =
        rows.findByBatchIdAndStatusInAndSectionInAndBlockIn(
                batch.getId(),
                EnumSet.allOf(TakeOnRowStatus.class),
                EnumSet.allOf(TakeOnSection.class),
                Set.of(Boolean.TRUE, Boolean.FALSE),
                PageRequest.of(
                    number,
                    size,
                    Sort.by("section").ascending().and(Sort.by("rowNumber").ascending())))
            .map(TakeOnBatchQueryService::row);
    return new TakeOnSignOffReviewResponse(
        batch.getId(),
        batch.getStatus(),
        batch.getTrustAccountId(),
        views.trustAccountName(batch.getTrustAccountId()),
        batch.getCutoverDate(),
        batch.isMidMonthAcknowledged(),
        TakeOnBatchViews.position(batch),
        views.counts(batch),
        TakeOnBatchViews.validationSummary(batch),
        batch.getValidationHash(),
        batch.getValidatedAt(),
        memberNames.resolveNameOrNull(batch.getCreatedBy()),
        declaration(batch).orElse(null),
        live.map(TakeOnBatchViews::signOff).orElse(null),
        signedByCaller,
        actions,
        new PagedModel<>(page));
  }

  /**
   * The declaration this batch's sign-off stores; empty without a firm, account or cutover date.
   */
  private Optional<TakeOnSignOffDeclarationResponse> declaration(TakeOnBatch batch) {
    String account = views.trustAccountName(batch.getTrustAccountId());
    if (account == null || batch.getCutoverDate() == null) {
      return Optional.empty();
    }
    return views
        .firmName()
        .map(
            firm ->
                new TakeOnSignOffDeclarationResponse(
                    TakeOnStatements.TAKEON_SIGNOFF_V1,
                    TakeOnStatements.trustSignOff(firm, account, batch.getCutoverDate())));
  }

  /** The mapping step's batch defaults (field key → value for blank cells), of the one source. */
  private Map<String, String> mappingDefaults(UUID batchId) {
    var merged = new LinkedHashMap<String, String>();
    for (var source : sources.findByBatchIdOrderBySectionAsc(batchId)) {
      var mapping = TakeOnColumnMapping.fromMap(source.getColumnMapping());
      if (mapping != null) {
        merged.putAll(mapping.defaults());
      }
    }
    return merged;
  }

  private TakeOnBatch lock(UUID batchId) {
    return batches
        .findByIdForUpdate(batchId)
        .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId));
  }
}
