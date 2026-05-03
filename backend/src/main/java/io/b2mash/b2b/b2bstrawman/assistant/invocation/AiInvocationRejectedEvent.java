package io.b2mash.b2b.b2bstrawman.assistant.invocation;

/**
 * Published after an invocation transitions to REJECTED.
 *
 * <p>515A only declares and publishes the event; subscribers belong to later slices.
 */
public record AiInvocationRejectedEvent(AiSpecialistInvocation invocation, String reason) {}
