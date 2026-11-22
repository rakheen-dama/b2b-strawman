package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a trust-write endpoint as idempotent under a client-generated {@code Idempotency-Key}
 * header (Phase 92 follow-up 648B F-11, founder ruling 2026-11-22). Enforced by {@link
 * TrustIdempotencyInterceptor}:
 *
 * <ul>
 *   <li>a request without the header, or with one that is not a UUID, is refused with 400;
 *   <li>a retry with the same key and the same request replays the first response (status, body,
 *       Content-Type, Location) without running the write again;
 *   <li>the same key with a different request, or from a different member, is refused with 409;
 *   <li>a key whose first request is still running, or ended without a stored response, is refused
 *       with 409 and never re-executed.
 * </ul>
 *
 * <p>Every mutating mapping under {@code verticals.legal.trustaccounting} must carry it ({@code
 * TrustIdempotencyCoverageTest}).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotentTrustWrite {}
