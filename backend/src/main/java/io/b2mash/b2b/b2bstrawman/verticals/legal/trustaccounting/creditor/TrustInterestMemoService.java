package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memo records for at-source sweeps (Phase 92 650B, ADR-384, §16.3.13): the Rule 54.28 gross
 * interest, gross charges and VAT of one trust account for one month that never reached the
 * account. A memo posts nothing — it goes through no posting machinery, takes no account lock and
 * moves no term of the equation — so it may be saved or corrected for any past or current month,
 * including one whose Rule 54.15 list is locked (it is not part of the list). No maker-checker (no
 * money moves); every save is audited as {@code trust_interest_memo.saved}, with the previous
 * figures on an update. Read by the Rule 54.28 schedule and the Section 35 pack (650C).
 */
@Service
public class TrustInterestMemoService {

  private static final String MODULE_ID = "trust_accounting";
  private static final String SAVE_TITLE = "Cannot save memo";
  private static final DateTimeFormatter MONTH =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

  private final TrustInterestMemoRepository memoRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final MemberNameResolver memberNameResolver;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final Clock clock;

  public TrustInterestMemoService(
      TrustInterestMemoRepository memoRepository,
      TrustAccountRepository trustAccountRepository,
      MemberNameResolver memberNameResolver,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      Clock clock) {
    this.memoRepository = memoRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.memberNameResolver = memberNameResolver;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.clock = clock;
  }

  // --- DTO Records ---

  /**
   * Save the month's memo (§16.3.13; upsert). Each amount is zero or more in rand and cents; the
   * VAT is included in the gross charges, so it is never more than them; the source note says where
   * the figures came from (a bank advice reference, a Fund statement).
   */
  public record SaveInterestMemoRequest(
      @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal grossInterest,
      @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal grossCharges,
      @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal chargesVat,
      @NotBlank @Size(max = 500) String sourceNote) {}

  /**
   * One memo (§16.4.2). {@code periodMonth} is the first day of the month; {@code recordedBy} the
   * member who saved it last, with their name.
   */
  public record InterestMemoResponse(
      UUID id,
      UUID trustAccountId,
      LocalDate periodMonth,
      BigDecimal grossInterest,
      BigDecimal grossCharges,
      BigDecimal chargesVat,
      String sourceNote,
      UUID recordedBy,
      String recordedByName,
      Instant createdAt,
      Instant updatedAt) {}

  // --- Service Methods ---

  /**
   * Saves the account's memo for {@code month}: creates it, or replaces the figures of the one
   * already saved. Refuses a month after the current one and a VAT above the gross charges; never
   * refuses a locked month (ADR-384).
   */
  @Transactional
  public InterestMemoResponse save(
      UUID trustAccountId, YearMonth month, SaveInterestMemoRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    if (!trustAccountRepository.existsById(trustAccountId)) {
      throw new ResourceNotFoundException("TrustAccount", trustAccountId);
    }
    if (month.isAfter(YearMonth.now(clock))) {
      throw new InvalidStateException(
          SAVE_TITLE,
          "A memo can be saved for this month or an earlier one, not for "
              + month.format(MONTH)
              + ".");
    }
    var grossInterest = money(request.grossInterest());
    var grossCharges = money(request.grossCharges());
    var chargesVat = money(request.chargesVat());
    if (chargesVat.compareTo(grossCharges) > 0) {
      throw new InvalidStateException(
          SAVE_TITLE, "The VAT cannot be more than the gross charges it is included in.");
    }
    var sourceNote = request.sourceNote().strip();
    var memberId = RequestScopes.requireMemberId();

    var details = new LinkedHashMap<String, Object>();
    details.put("trust_account_id", trustAccountId.toString());
    details.put("period_month", month.toString());
    details.put("gross_interest", grossInterest.toPlainString());
    details.put("gross_charges", grossCharges.toPlainString());
    details.put("charges_vat", chargesVat.toPlainString());
    details.put("source_note", sourceNote);

    var existing = memoRepository.findForUpdate(trustAccountId, month.atDay(1));
    TrustInterestMemo memo;
    if (existing.isPresent()) {
      memo = existing.get();
      details.put("previous_gross_interest", memo.getGrossInterest().toPlainString());
      details.put("previous_gross_charges", memo.getGrossCharges().toPlainString());
      details.put("previous_charges_vat", memo.getChargesVat().toPlainString());
      details.put("previous_source_note", memo.getSourceNote());
      memo.correct(grossInterest, grossCharges, chargesVat, sourceNote, memberId);
      memo = memoRepository.saveAndFlush(memo);
    } else {
      try {
        memo =
            memoRepository.saveAndFlush(
                new TrustInterestMemo(
                    trustAccountId,
                    month,
                    grossInterest,
                    grossCharges,
                    chargesVat,
                    sourceNote,
                    memberId));
      } catch (DataIntegrityViolationException e) {
        // Another save for the same account and month committed first (uq_trust_interest_memo_*)
        throw new ResourceConflictException(
            "Memo saved at the same time",
            "Another member saved the memo for "
                + month.format(MONTH)
                + " on this trust account at the same time. Reload the page and try again.");
      }
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_interest_memo.saved")
            .entityType("trust_interest_memo")
            .entityId(memo.getId())
            .details(details)
            .build());
    return toResponse(memo, memberNameResolver.resolveNameOrNull(memo.getRecordedBy()));
  }

  /** The account's memos of {@code year} (the current year when null), oldest month first. */
  @Transactional(readOnly = true)
  public List<InterestMemoResponse> list(UUID trustAccountId, Integer year) {
    moduleGuard.requireModule(MODULE_ID);
    if (!trustAccountRepository.existsById(trustAccountId)) {
      throw new ResourceNotFoundException("TrustAccount", trustAccountId);
    }
    int y = year != null ? year : LocalDate.now(clock).getYear();
    if (y < 1900 || y > 9999) {
      throw new InvalidStateException("Invalid year", "Choose a year from 1900 to 9999.");
    }
    var memos =
        memoRepository.findByTrustAccountIdAndPeriodMonthBetweenOrderByPeriodMonthAsc(
            trustAccountId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 1));
    if (memos.isEmpty()) {
      return List.of();
    }
    var names =
        memberNameResolver.resolveNames(
            memos.stream().map(TrustInterestMemo::getRecordedBy).collect(Collectors.toSet()));
    return memos.stream().map(m -> toResponse(m, names.get(m.getRecordedBy()))).toList();
  }

  private static BigDecimal money(BigDecimal value) {
    // @Digits already bounds the fraction to two places: this only normalises the scale.
    return value.setScale(2, RoundingMode.UNNECESSARY);
  }

  private static InterestMemoResponse toResponse(TrustInterestMemo memo, String recordedByName) {
    return new InterestMemoResponse(
        memo.getId(),
        memo.getTrustAccountId(),
        memo.getPeriodMonth(),
        memo.getGrossInterest(),
        memo.getGrossCharges(),
        memo.getChargesVat(),
        memo.getSourceNote(),
        memo.getRecordedBy(),
        recordedByName,
        memo.getCreatedAt(),
        memo.getUpdatedAt());
  }
}
