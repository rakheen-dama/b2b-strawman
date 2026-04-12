package io.b2mash.b2b.b2bstrawman.settings.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for org settings endpoints. Aggregates org configuration, branding, compliance, tax,
 * billing, vertical/horizontal modules, and data protection fields.
 */
public record SettingsResponse(
    String orgName,
    String defaultCurrency,
    String logoUrl,
    String brandColor,
    String documentFooterText,
    Integer dormancyThresholdDays,
    Integer dataRequestDeadlineDays,
    List<Map<String, Object>> compliancePackStatus,
    boolean accountingEnabled,
    boolean aiEnabled,
    boolean documentSigningEnabled,
    String taxRegistrationNumber,
    String taxRegistrationLabel,
    String taxLabel,
    boolean taxInclusive,
    Integer acceptanceExpiryDays,
    Integer defaultRequestReminderDays,
    boolean timeReminderEnabled,
    String timeReminderDays,
    String timeReminderTime,
    Double timeReminderMinHours,
    BigDecimal defaultExpenseMarkupPercent,
    BigDecimal defaultWeeklyCapacityHours,
    Integer billingBatchAsyncThreshold,
    Integer billingEmailRateLimit,
    String defaultBillingRunCurrency,
    String projectNamingPattern,
    String verticalProfile,
    List<String> enabledModules,
    String terminologyNamespace,
    // Phase 50: Data protection fields
    String dataProtectionJurisdiction,
    Boolean retentionPolicyEnabled,
    Integer defaultRetentionMonths,
    Integer financialRetentionMonths,
    String informationOfficerName,
    String informationOfficerEmail) {}
