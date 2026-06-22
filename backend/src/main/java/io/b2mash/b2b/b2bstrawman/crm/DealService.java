package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealResponse;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealUpdateRequest;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.exception.DeleteGuard;
import io.b2mash.b2b.b2bstrawman.proposal.ProposalRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD + filtered-list service for {@link Deal} (Phase 80, slice 574A). Mirrors {@code
 * ProposalService} in shape: constructor injection, {@code @Transactional}, deal-number allocation,
 * and audit via {@link AuditEventBuilder}/{@link AuditService}.
 *
 * <p>Derived read-model fields ({@code stageName}, {@code effectiveProbabilityPct}, {@code
 * weightedValue}) are resolved here so the controller stays a thin HTTP adapter.
 *
 * <p>Scope boundary (574A): this service NEVER performs stage/status transitions ({@code
 * markWon}/{@code markLost}/{@code reopen}/{@code moveToOpenStage}) and never emits {@code
 * deal.stage_changed}/{@code deal.won}/{@code deal.lost} events — those land in 575A.
 */
@Service
public class DealService {

  private static final Logger log = LoggerFactory.getLogger(DealService.class);
  private static final String DEFAULT_CURRENCY = "ZAR";

  private final DealRepository dealRepository;
  private final DealNumberService dealNumberService;
  private final PipelineStageService pipelineStageService;
  private final PipelineStageRepository pipelineStageRepository;
  private final CustomerService customerService;
  private final OrgSettingsRepository orgSettingsRepository;
  private final AuditService auditService;
  private final ProposalRepository proposalRepository;

  public DealService(
      DealRepository dealRepository,
      DealNumberService dealNumberService,
      PipelineStageService pipelineStageService,
      PipelineStageRepository pipelineStageRepository,
      CustomerService customerService,
      OrgSettingsRepository orgSettingsRepository,
      AuditService auditService,
      ProposalRepository proposalRepository) {
    this.dealRepository = dealRepository;
    this.dealNumberService = dealNumberService;
    this.pipelineStageService = pipelineStageService;
    this.pipelineStageRepository = pipelineStageRepository;
    this.customerService = customerService;
    this.orgSettingsRepository = orgSettingsRepository;
    this.auditService = auditService;
    this.proposalRepository = proposalRepository;
  }

  /**
   * Creates an OPEN deal against an EXISTING customer. The customer is validated (404 if absent),
   * the stage resolved ({@code stageId} if given, else the first OPEN stage), a deal number
   * allocated, and the currency resolved from {@link OrgSettings}.
   */
  @Transactional
  public DealResponse createDeal(
      UUID customerId,
      String title,
      UUID stageId,
      BigDecimal valueAmount,
      UUID ownerId,
      String source,
      LocalDate expectedCloseDate,
      UUID createdBy) {
    customerService.getCustomer(customerId);
    return createDealInternal(
        customerId, title, stageId, valueAmount, ownerId, source, expectedCloseDate, createdBy);
  }

  /**
   * Shared deal-creation path used by the public {@link #createDeal} entry point and by {@link
   * DealIntakeService} (which resolves/creates the customer before delegating here). Performs stage
   * resolution, currency resolution, deal-number allocation, persistence, and the {@code
   * deal.created} audit emission.
   *
   * <p>The caller is responsible for validating that {@code customerId} references an existing
   * customer in the current tenant. It runs within the caller's transaction — both {@link
   * #createDeal} and {@link DealIntakeService#intake} are {@code @Transactional}.
   */
  DealResponse createDealInternal(
      UUID customerId,
      String title,
      UUID stageId,
      BigDecimal valueAmount,
      UUID ownerId,
      String source,
      LocalDate expectedCloseDate,
      UUID createdBy) {
    var stage = resolveStage(stageId);
    var number = dealNumberService.allocateNumber();
    var currency = resolveCurrency();
    var deal =
        Deal.create(
            number,
            customerId,
            title,
            stage,
            valueAmount,
            currency,
            ownerId != null ? ownerId : createdBy,
            source,
            createdBy);
    if (expectedCloseDate != null) {
      deal.updateExpectedClose(expectedCloseDate);
    }
    var saved = dealRepository.save(deal);
    auditCreated(saved, number, title, customerId);
    log.info("Created deal {} ({}) for customer {}", saved.getId(), number, customerId);
    return toResponse(saved, stage);
  }

  @Transactional(readOnly = true)
  public DealResponse getDeal(UUID dealId) {
    var deal = dealRepository.findOneById(dealId);
    return toResponse(deal, pipelineStageRepository.findOneById(deal.getStageId()));
  }

  @Transactional
  public DealResponse updateDeal(UUID dealId, DealUpdateRequest request) {
    var deal = dealRepository.findOneById(dealId);

    if (request.title() != null) {
      deal.updateTitle(request.title());
    }
    if (request.valueAmount() != null || request.valueCurrency() != null) {
      var amount = request.valueAmount() != null ? request.valueAmount() : deal.getValueAmount();
      var currency =
          request.valueCurrency() != null ? request.valueCurrency() : deal.getValueCurrency();
      deal.updateValue(amount, currency);
    }
    if (request.ownerId() != null) {
      deal.updateOwner(request.ownerId());
    }
    if (request.expectedCloseDate() != null) {
      deal.updateExpectedClose(request.expectedCloseDate());
    }
    if (request.probabilityOverride() != null) {
      deal.updateProbabilityOverride(request.probabilityOverride());
    }
    if (request.source() != null) {
      deal.updateSource(request.source());
    }
    if (request.customFields() != null) {
      deal.setCustomFields(request.customFields());
    }

    var saved = dealRepository.save(deal);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("deal.updated")
            .entityType("deal")
            .entityId(saved.getId())
            .details(Map.of("deal_number", saved.getDealNumber()))
            .build());
    log.info("Updated deal {}", dealId);
    return toResponse(saved, pipelineStageRepository.findOneById(saved.getStageId()));
  }

  @Transactional
  public void deleteDeal(UUID dealId) {
    var deal = dealRepository.findOneById(dealId);
    DeleteGuard.forEntity("deal", dealId, "delete")
        .checkNotExists(
            "linked proposals",
            () -> proposalRepository.existsByDealId(dealId),
            "Unlink or delete the linked proposals first.")
        .execute();
    dealRepository.delete(deal);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("deal.deleted")
            .entityType("deal")
            .entityId(dealId)
            .details(Map.of("deal_number", deal.getDealNumber(), "title", deal.getTitle()))
            .build());
    log.info("Deleted deal {}", dealId);
  }

  /**
   * Filtered, paged list mapped to {@link DealResponse}. Stages are batch-loaded once into a map to
   * resolve {@code stageName}/{@code effectiveProbabilityPct} per row without an N+1.
   */
  @Transactional(readOnly = true)
  public Page<DealResponse> listDeals(
      UUID stageId,
      UUID ownerId,
      UUID customerId,
      DealStatus status,
      String source,
      LocalDate fromDate,
      LocalDate toDate,
      Pageable pageable) {
    var page =
        dealRepository.findFiltered(
            stageId, ownerId, customerId, status, source, fromDate, toDate, pageable);
    Map<UUID, PipelineStage> stagesById =
        pipelineStageService.listStages().stream()
            .collect(Collectors.toMap(PipelineStage::getId, Function.identity()));
    return page.map(deal -> toResponse(deal, stagesById.get(deal.getStageId())));
  }

  // --- Helpers ---

  private PipelineStage resolveStage(UUID stageId) {
    return stageId != null
        ? pipelineStageRepository.findOneById(stageId)
        : pipelineStageService.firstOpenStage();
  }

  private String resolveCurrency() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(OrgSettings::getDefaultCurrency)
        .filter(c -> c != null && !c.isBlank())
        .orElse(DEFAULT_CURRENCY);
  }

  private DealResponse toResponse(Deal deal, PipelineStage stage) {
    int stageDefault = stage != null ? stage.getDefaultProbabilityPct() : 0;
    String stageName = stage != null ? stage.getName() : null;
    int effectiveProb = deal.effectiveProbabilityPct(stageDefault);
    return DealResponse.from(deal, effectiveProb, stageName);
  }

  private void auditCreated(Deal deal, String number, String title, UUID customerId) {
    var details = new HashMap<String, Object>();
    details.put("deal_number", number);
    details.put("title", title);
    details.put("customer_id", customerId.toString());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("deal.created")
            .entityType("deal")
            .entityId(deal.getId())
            .details(details)
            .build());
  }
}
