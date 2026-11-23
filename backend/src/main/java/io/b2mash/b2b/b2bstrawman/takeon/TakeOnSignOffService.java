package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.CapabilityAuthorizationService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnAttestationRequest;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The FICA attestation of a clients batch (Phase 93 655A, §17.3.3, FD-5, FD-37) and the revoke of a
 * live attestation or sign-off before commit. The trust half (sign-off, review, withdrawal) is
 * 657C.
 *
 * <p>Attest: {@code MANAGE_TAKE_ON} (annotation) + {@code CUSTOMER_MANAGEMENT} (access policy) +
 * {@code MANAGE_COMPLIANCE} (here). The batch row is locked {@code FOR UPDATE} before any check
 * (D-33), so attestation serialises with re-validation and the commit claim (X5).
 */
@Service
public class TakeOnSignOffService {

  static final String COMPLIANCE_CAPABILITY = "MANAGE_COMPLIANCE";

  private final TakeOnBatchRepository batches;
  private final TakeOnSourceRepository sources;
  private final TakeOnSignOffRepository signOffs;
  private final TakeOnAccessPolicy accessPolicy;
  private final CapabilityAuthorizationService capabilities;
  private final MemberRepository members;
  private final TakeOnBatchViews views;
  private final TakeOnAudit audit;

  public TakeOnSignOffService(
      TakeOnBatchRepository batches,
      TakeOnSourceRepository sources,
      TakeOnSignOffRepository signOffs,
      TakeOnAccessPolicy accessPolicy,
      CapabilityAuthorizationService capabilities,
      MemberRepository members,
      TakeOnBatchViews views,
      TakeOnAudit audit) {
    this.batches = batches;
    this.sources = sources;
    this.signOffs = signOffs;
    this.accessPolicy = accessPolicy;
    this.capabilities = capabilities;
    this.members = members;
    this.views = views;
    this.audit = audit;
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
    UUID memberId = RequestScopes.requireMemberId();
    var member =
        members
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
    String roleName =
        member.getOrgRoleEntity() == null ? "Member" : member.getOrgRoleEntity().getName();
    var signOff =
        new TakeOnSignOff(
            batchId,
            TakeOnSignOffKind.FICA_ATTESTATION,
            memberId,
            member.getName(),
            roleName,
            TakeOnStatements.FICA_ATTEST_V1,
            TakeOnStatements.ficaAttestation(firm),
            batch.getValidationHash(),
            defaults.isEmpty() ? null : new LinkedHashMap<>(defaults));
    try {
      signOffs.saveAndFlush(signOff);
    } catch (DataIntegrityViolationException e) {
      throw TakeOnProblems.alreadyAttested(); // uq_take_on_sign_offs_live
    }
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
   * TAKE_ON_ATTESTATION_REQUIRED}; for a dataset that needs none, {@link
   * TakeOnSignOff#notRequired}. Called by the claim under the batch lock.
   */
  TakeOnSignOff requireLiveFor(TakeOnBatch batch, TakeOnSignOffKind kind) {
    if (kind == null) {
      return TakeOnSignOff.notRequired(Objects.requireNonNull(batch.getValidationHash()));
    }
    return signOffs
        .findByBatchIdAndKindAndRevokedAtIsNull(batch.getId(), kind)
        .orElseThrow(TakeOnProblems::attestationRequired);
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
