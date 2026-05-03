package io.b2mash.b2b.b2bstrawman.assistant.invocation;

/**
 * Published after an invocation transitions to APPROVED and the applier has run successfully.
 *
 * <p>515A only declares and publishes the event; subscribers belong to later slices.
 */
public record AiInvocationApprovedEvent(AiSpecialistInvocation invocation) {}
