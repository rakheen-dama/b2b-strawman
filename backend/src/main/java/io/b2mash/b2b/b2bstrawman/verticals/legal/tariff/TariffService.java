package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TariffService {

  private static final String MODULE_ID = "lssa_tariff";

  private final TariffScheduleRepository scheduleRepository;
  private final TariffItemRepository itemRepository;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;

  public TariffService(
      TariffScheduleRepository scheduleRepository,
      TariffItemRepository itemRepository,
      VerticalModuleGuard moduleGuard,
      AuditService auditService) {
    this.scheduleRepository = scheduleRepository;
    this.itemRepository = itemRepository;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
  }

  // --- DTO Records ---

  public record CreateScheduleRequest(
      @NotBlank String name,
      @NotBlank String category,
      @NotBlank String courtLevel,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String source) {}

  public record UpdateScheduleRequest(
      @NotBlank String name,
      @NotBlank String category,
      @NotBlank String courtLevel,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo,
      boolean isActive,
      String source) {}

  public record ScheduleResponse(
      UUID id,
      String name,
      String category,
      String courtLevel,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      boolean isActive,
      boolean isSystem,
      String source,
      int itemCount,
      Instant createdAt,
      Instant updatedAt) {}

  public record CreateItemRequest(
      @NotBlank String itemNumber,
      @NotBlank String section,
      @NotBlank String description,
      @NotNull BigDecimal amount,
      @NotBlank String unit,
      String notes,
      int sortOrder) {}

  public record UpdateItemRequest(
      @NotBlank String itemNumber,
      @NotBlank String section,
      @NotBlank String description,
      @NotNull BigDecimal amount,
      @NotBlank String unit,
      String notes,
      int sortOrder) {}

  public record TariffItemResponse(
      UUID id,
      UUID scheduleId,
      String itemNumber,
      String section,
      String description,
      BigDecimal amount,
      String unit,
      String notes,
      int sortOrder) {}

  // --- Schedule Methods ---

  @Transactional(readOnly = true)
  public List<ScheduleResponse> listSchedules() {
    return scheduleRepository.findAll().stream().map(this::toScheduleResponse).toList();
  }

  @Transactional(readOnly = true)
  public ScheduleResponse getSchedule(UUID id) {
    var schedule =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TariffSchedule", id));
    return toScheduleResponse(schedule);
  }

  @Transactional(readOnly = true)
  public List<ScheduleResponse> getActiveSchedule(String category, String courtLevel) {
    return scheduleRepository
        .findByCategoryAndCourtLevelAndIsActiveTrue(category, courtLevel)
        .stream()
        .map(this::toScheduleResponse)
        .toList();
  }

  @Transactional
  public ScheduleResponse createSchedule(CreateScheduleRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var schedule =
        new TariffSchedule(
            request.name(),
            request.category(),
            request.courtLevel(),
            request.effectiveFrom(),
            request.effectiveTo(),
            request.source());

    var saved = scheduleRepository.save(schedule);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tariff_schedule.created")
            .entityType("tariff_schedule")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "name", saved.getName(),
                    "category", saved.getCategory(),
                    "court_level", saved.getCourtLevel()))
            .build());

    return toScheduleResponse(saved);
  }

  @Transactional
  public ScheduleResponse updateSchedule(UUID id, UpdateScheduleRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var schedule =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TariffSchedule", id));

    if (schedule.isSystem()) {
      throw new InvalidStateException(
          "System schedule is read-only",
          "Cannot modify system tariff schedule. Clone it to create a custom copy.");
    }

    schedule.setName(request.name());
    schedule.setCategory(request.category());
    schedule.setCourtLevel(request.courtLevel());
    schedule.setEffectiveFrom(request.effectiveFrom());
    schedule.setEffectiveTo(request.effectiveTo());
    schedule.setActive(request.isActive());
    schedule.setSource(request.source());
    schedule.setUpdatedAt(Instant.now());

    var saved = scheduleRepository.save(schedule);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tariff_schedule.updated")
            .entityType("tariff_schedule")
            .entityId(saved.getId())
            .details(Map.of("name", saved.getName(), "category", saved.getCategory()))
            .build());

    return toScheduleResponse(saved);
  }

  @Transactional
  public ScheduleResponse cloneSchedule(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var source =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TariffSchedule", id));

    var clone =
        new TariffSchedule(
            source.getName() + " (Copy)",
            source.getCategory(),
            source.getCourtLevel(),
            source.getEffectiveFrom(),
            source.getEffectiveTo(),
            source.getSource());

    // Deep-copy items
    for (var item : source.getItems()) {
      var clonedItem =
          new TariffItem(
              clone,
              item.getItemNumber(),
              item.getSection(),
              item.getDescription(),
              item.getAmount(),
              item.getUnit(),
              item.getNotes(),
              item.getSortOrder());
      clone.getItems().add(clonedItem);
    }

    var saved = scheduleRepository.save(clone);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tariff_schedule.cloned")
            .entityType("tariff_schedule")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "source_id", source.getId().toString(),
                    "name", saved.getName(),
                    "items_count", saved.getItems().size()))
            .build());

    return toScheduleResponse(saved);
  }

  // --- Item Methods ---

  @Transactional(readOnly = true)
  public TariffItemResponse getItem(UUID id) {
    var item =
        itemRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TariffItem", id));
    return toItemResponse(item);
  }

  @Transactional
  public TariffItemResponse createItem(UUID scheduleId, CreateItemRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var schedule =
        scheduleRepository
            .findById(scheduleId)
            .orElseThrow(() -> new ResourceNotFoundException("TariffSchedule", scheduleId));

    if (schedule.isSystem()) {
      throw new InvalidStateException(
          "System schedule is read-only",
          "Cannot add items to system tariff schedule. Clone it to create a custom copy.");
    }

    var item =
        new TariffItem(
            schedule,
            request.itemNumber(),
            request.section(),
            request.description(),
            request.amount(),
            request.unit(),
            request.notes(),
            request.sortOrder());

    var saved = itemRepository.save(item);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tariff_item.created")
            .entityType("tariff_item")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "schedule_id", scheduleId.toString(),
                    "item_number", saved.getItemNumber(),
                    "description", saved.getDescription()))
            .build());

    return toItemResponse(saved);
  }

  @Transactional
  public TariffItemResponse updateItem(UUID id, UpdateItemRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var item =
        itemRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TariffItem", id));

    if (item.getSchedule().isSystem()) {
      throw new InvalidStateException(
          "System schedule is read-only",
          "Cannot modify items on system tariff schedule. Clone the schedule first.");
    }

    item.setItemNumber(request.itemNumber());
    item.setSection(request.section());
    item.setDescription(request.description());
    item.setAmount(request.amount());
    item.setUnit(request.unit());
    item.setNotes(request.notes());
    item.setSortOrder(request.sortOrder());

    var saved = itemRepository.save(item);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tariff_item.updated")
            .entityType("tariff_item")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "item_number", saved.getItemNumber(),
                    "description", saved.getDescription()))
            .build());

    return toItemResponse(saved);
  }

  @Transactional
  public void deleteItem(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var item =
        itemRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TariffItem", id));

    if (item.getSchedule().isSystem()) {
      throw new InvalidStateException(
          "System schedule is read-only",
          "Cannot delete items from system tariff schedule. Clone the schedule first.");
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tariff_item.deleted")
            .entityType("tariff_item")
            .entityId(item.getId())
            .details(
                Map.of(
                    "item_number", item.getItemNumber(),
                    "description", item.getDescription()))
            .build());

    itemRepository.delete(item);
  }

  @Transactional(readOnly = true)
  public List<TariffItemResponse> searchItems(UUID scheduleId, String search, String section) {
    if (search != null && !search.isBlank()) {
      return itemRepository.searchByDescription(scheduleId, search).stream()
          .map(this::toItemResponse)
          .toList();
    }
    if (section != null && !section.isBlank()) {
      return itemRepository
          .findByScheduleIdAndSectionOrderBySortOrderAsc(scheduleId, section)
          .stream()
          .map(this::toItemResponse)
          .toList();
    }
    return itemRepository.findByScheduleIdOrderBySortOrderAsc(scheduleId).stream()
        .map(this::toItemResponse)
        .toList();
  }

  // --- Mappers ---

  private ScheduleResponse toScheduleResponse(TariffSchedule schedule) {
    return new ScheduleResponse(
        schedule.getId(),
        schedule.getName(),
        schedule.getCategory(),
        schedule.getCourtLevel(),
        schedule.getEffectiveFrom(),
        schedule.getEffectiveTo(),
        schedule.isActive(),
        schedule.isSystem(),
        schedule.getSource(),
        schedule.getItems().size(),
        schedule.getCreatedAt(),
        schedule.getUpdatedAt());
  }

  private TariffItemResponse toItemResponse(TariffItem item) {
    return new TariffItemResponse(
        item.getId(),
        item.getSchedule().getId(),
        item.getItemNumber(),
        item.getSection(),
        item.getDescription(),
        item.getAmount(),
        item.getUnit(),
        item.getNotes(),
        item.getSortOrder());
  }
}
