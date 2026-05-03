package io.b2mash.b2b.b2bstrawman.assistant.invocation.payload;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed marker interface for AI specialist proposed/applied output payloads.
 *
 * <p>Per ADR-148/270, payloads are persisted as JSONB on {@code ai_specialist_invocations} with a
 * {@code kind} discriminator so a single table accommodates all four specialist output shapes.
 *
 * <p>The four permitted records are stub no-arg records in 515A; 512A/513A/514A replace them with
 * the real fields per architecture §2.4. Jackson polymorphism is wired by {@code kind} below so
 * deserialisation continues to work as soon as real fields land.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BillingPolishPayload.class, name = "BillingPolishPayload"),
  @JsonSubTypes.Type(value = BillingGroupingPayload.class, name = "BillingGroupingPayload"),
  @JsonSubTypes.Type(value = IntakeExtractionPayload.class, name = "IntakeExtractionPayload"),
  @JsonSubTypes.Type(value = InboxSummaryPayload.class, name = "InboxSummaryPayload")
})
public sealed interface OutputPayload
    permits BillingPolishPayload,
        BillingGroupingPayload,
        IntakeExtractionPayload,
        InboxSummaryPayload {}
