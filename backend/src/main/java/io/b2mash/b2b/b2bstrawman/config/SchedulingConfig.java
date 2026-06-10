package io.b2mash.b2b.b2bstrawman.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Gates Spring's scheduler registration behind {@code kazi.scheduling.enabled}.
 *
 * <p>{@code @EnableScheduling} previously sat unconditionally on {@code BackendApplication}, so the
 * scheduler registrar activated in <em>every</em> Spring context — including the many
 * {@code @SpringBootTest} contexts, which boot the full application. That meant all
 * {@code @Scheduled} methods (AccountingSyncWorker's 30s drain, AutomationScheduler's 60s poll, and
 * ~20 others) fired during the test suite, fanning out per-tenant jobs, dead-lettering them, and
 * churning the embedded DB. This produced "Job dead-lettered ... accounting_sync_drain" ERROR noise
 * and was a source of the JobWorkerIntegrationTest flake fixed in PR #1406.
 *
 * <p>An {@code application-test.yml} block set {@code spring.scheduling.enabled: false} to suppress
 * this, but that is not a real Spring Boot property — nothing reads it, so it was dead config
 * documenting an unfulfilled intent. This class replaces that with a real toggle.
 *
 * <p>{@code matchIfMissing = true} keeps scheduling <strong>on by default</strong>, so production,
 * dev, and local behaviour is unchanged with no config edits. Tests set {@code
 * kazi.scheduling.enabled: false} to opt out. A test class that genuinely needs a startup-fired or
 * polling scheduler can opt back in via {@code @TestPropertySource(properties =
 * "kazi.scheduling.enabled=true")}.
 *
 * <p>Note: {@code ShedLockConfig}'s {@code @EnableSchedulerLock} is independent of this — it only
 * registers an AOP advisor for {@code @SchedulerLock}, which is harmless with no scheduled methods
 * running.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "kazi.scheduling.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulingConfig {}
