package io.b2mash.b2b.b2bstrawman.crm.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Deal update request (Phase 80, §11.4). All fields are nullable — only the supplied fields are
 * applied via the entity's editable-in-any-status setters. Status/stage transitions are NOT
 * performed here (they land in 575A).
 *
 * @param title new title (nullable)
 * @param valueAmount new value amount (nullable)
 * @param valueCurrency new value currency (nullable)
 * @param ownerId new owner (nullable)
 * @param expectedCloseDate new expected close date (nullable)
 * @param probabilityOverride explicit probability override 0..100 (nullable)
 * @param source new lead source (nullable)
 * @param customFields custom field values (nullable)
 */
public record DealUpdateRequest(
    @Size(min = 1, max = 200, message = "title must be between 1 and 200 characters") String title,
    BigDecimal valueAmount,
    String valueCurrency,
    UUID ownerId,
    LocalDate expectedCloseDate,
    @jakarta.validation.constraints.Min(
            value = 0,
            message = "probabilityOverride must be between 0 and 100")
        @jakarta.validation.constraints.Max(
            value = 100,
            message = "probabilityOverride must be between 0 and 100")
        Integer probabilityOverride,
    String source,
    Map<String, Object> customFields) {}
