package io.b2mash.b2b.b2bstrawman.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.elements.ClassesShouldConjunction;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * G88-F48 / G88-F49 guard: server "today" is South African time (Africa/Johannesburg), never the
 * JVM default zone (containers run in UTC). Business code reads the date from the injected {@link
 * Clock} bean ({@code LocalDate.now(clock)}), which {@code BackendApplication} builds in {@link
 * KaziTime#ZONE}; converting a stored instant to a business date uses {@code KaziTime.ZONE}.
 *
 * <ol>
 *   <li>No zone-less {@code now()} on any {@code java.time} date/time type, and no JVM-default zone
 *       lookups — no exemptions.
 *   <li>No {@code now(ZoneId)} overloads (they bypass the test-pinnable clock and are the only way
 *       a {@code LocalDate.now(ZoneOffset.UTC)} regression can hide), and no {@code Clock}
 *       factories outside the bean, except the commented allowlist below.
 *   <li>Every {@code @Scheduled(cron)} names {@code zone = KaziTime.ZONE_ID}.
 *   <li>No {@code ZoneOffset.UTC} and no {@code ZoneId.of(String)} outside the commented UTC
 *       allowlist: ArchUnit cannot read argument values, so naming a zone at all is the only
 *       bytecode-visible signal of a hand-rolled (usually UTC) business date, e.g. {@code
 *       instant.atZone(ZoneOffset.UTC).toLocalDate()}.
 *   <li>No legacy {@code java.util.Date} / {@code GregorianCalendar} constructors and no {@code
 *       Calendar.getInstance} — they read the JVM default zone or clock implicitly. No exemptions.
 * </ol>
 *
 * Allowlist entries carry their reason; adding one is a review-time decision, not a fix.
 */
@AnalyzeClasses(
    packages = "io.b2mash.b2b.b2bstrawman",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ZoneAwareTimeArchTest {

  private static final List<Class<?>> DATE_TIME_TYPES =
      List.of(
          LocalDate.class,
          LocalDateTime.class,
          LocalTime.class,
          YearMonth.class,
          ZonedDateTime.class,
          OffsetDateTime.class,
          Year.class,
          MonthDay.class);

  /** Classes allowed to call {@code now(ZoneId)} / a {@code Clock} factory, with the reason. */
  private static final Map<String, String> EXPLICIT_ZONE_ALLOWLIST =
      Map.of(
          "io.b2mash.b2b.b2bstrawman.BackendApplication",
          "builds the Clock bean itself: Clock.system(KaziTime.ZONE)",
          "io.b2mash.b2b.b2bstrawman.integration.ai.cost.AiCostService",
          "AI budget month is a provider billing month (UTC) by design",
          "io.b2mash.b2b.b2bstrawman.audit.export.AuditExportService",
          "audit export labels use UTC days, matching the audit-log UI's UTC-day filter",
          "io.b2mash.b2b.b2bstrawman.audit.export.AuditPdfExporter",
          "audit export labels use UTC days, matching the audit-log UI's UTC-day filter",
          "io.b2mash.b2b.b2bstrawman.demo.seed.BaseDemoDataSeeder",
          "demo seed dates: LocalDate.now(KaziTime.ZONE); abstract seeder base has no Clock");

  /**
   * Classes allowed to name {@code ZoneOffset.UTC} or parse a zone id, with the reason. Each is UTC
   * (or the zone constant) on purpose under the governing rule: wire formats, storage instants,
   * sort keys, provider billing months and audit-export timestamps stay UTC.
   */
  private static final Map<String, String> UTC_ALLOWLIST =
      Map.of(
          "io.b2mash.b2b.b2bstrawman.config.KaziTime",
          "defines the business zone itself: ZoneId.of(ZONE_ID)",
          "io.b2mash.b2b.b2bstrawman.integration.ai.cost.AiCostService",
          "AI budget month is a provider billing month (UTC) by design",
          "io.b2mash.b2b.b2bstrawman.audit.export.AuditExportService",
          "audit-export timestamps and labels are UTC, matching the audit-log UI's UTC-day filter",
          "io.b2mash.b2b.b2bstrawman.audit.export.AuditPdfExporter",
          "audit-export timestamps and labels are UTC, matching the audit-log UI's UTC-day filter",
          "io.b2mash.b2b.b2bstrawman.customerbackend.service.TrustLedgerPortalSyncService",
          "portal sort key: fallback occurredAt as the trust date at UTC midnight (ordering only)",
          "io.b2mash.b2b.b2bstrawman.integration.accounting.xero.XeroApiClient",
          "Xero wire format: DateTime(y,m,d,h,m,s) query filters are UTC");

  @ArchTest
  static final ArchRule no_utc_or_parsed_zone_outside_allowlist =
      noClasses()
          .that(
              DescribedPredicate.describe(
                  "are not on the UTC allowlist",
                  (JavaClass c) -> !UTC_ALLOWLIST.containsKey(topLevelName(c))))
          .should()
          .accessField(ZoneOffset.class, "UTC")
          .orShould()
          .callMethod(ZoneId.class, "of", String.class)
          .because(
              "a business date is South African time — use the Clock bean or KaziTime.ZONE; a"
                  + " deliberate UTC site (wire format, storage instant, sort key, provider billing"
                  + " month, audit export) goes on the commented UTC allowlist");

  @ArchTest
  static final ArchRule no_legacy_date_apis =
      noClasses()
          .should()
          .callConstructorWhere(target(owner(equivalentTo(Date.class))))
          .orShould()
          .callConstructorWhere(target(owner(equivalentTo(GregorianCalendar.class))))
          .orShould()
          .callMethodWhere(
              target(owner(equivalentTo(Calendar.class))).and(target(name("getInstance"))))
          .because(
              "java.util.Date / Calendar read the JVM default zone and clock implicitly — use"
                  + " java.time with the Clock bean (Date.from(instant) at a library boundary is"
                  + " fine)");

  @ArchTest
  static final ArchRule no_zone_less_now_or_jvm_default_zone =
      banZoneLessNow(noClasses().should().callMethod(Clock.class, "systemDefaultZone"))
          .orShould()
          .callMethod(ZoneId.class, "systemDefault")
          .orShould()
          .callMethod(TimeZone.class, "getDefault")
          .because(
              "server 'today' must be South African time — use LocalDate.now(clock) with the"
                  + " injected Clock bean (G88-F48), never the JVM default zone");

  @ArchTest
  static final ArchRule no_explicit_zone_now_outside_allowlist =
      banExplicitZoneNow(
              noClasses()
                  .that(
                      DescribedPredicate.describe(
                          "are not on the explicit-zone allowlist",
                          (JavaClass c) -> !EXPLICIT_ZONE_ALLOWLIST.containsKey(c.getName())))
                  .should()
                  .callMethod(Clock.class, "systemUTC"))
          .orShould()
          .callMethod(Clock.class, "system", ZoneId.class)
          .because(
              "now(ZoneId) bypasses the test-pinnable Clock bean and hides UTC regressions — use"
                  + " LocalDate.now(clock); a deliberate UTC site goes on the commented allowlist");

  private static final ArchCondition<JavaMethod> NAME_THE_SA_ZONE_ON_CRON =
      new ArchCondition<>("name zone = KaziTime.ZONE_ID when a cron is set") {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
          method
              .tryGetAnnotationOfType(Scheduled.class)
              .filter(s -> !s.cron().isEmpty())
              .filter(s -> !KaziTime.ZONE_ID.equals(s.zone()))
              .ifPresent(
                  s ->
                      events.add(
                          SimpleConditionEvent.violated(
                              method,
                              method.getFullName()
                                  + " cron '"
                                  + s.cron()
                                  + "' has zone '"
                                  + s.zone()
                                  + "'")));
        }
      };

  @ArchTest
  static final ArchRule cron_schedules_run_in_south_african_time =
      methods()
          .that()
          .areAnnotatedWith(Scheduled.class)
          .should(NAME_THE_SA_ZONE_ON_CRON)
          .because(
              "a zone-less cron fires in the JVM zone (UTC in containers), 2h late in SA time"
                  + " (G88-F48 J4)");

  /** Nested and anonymous classes (lambdas compile into their owner) share the owner's entry. */
  private static String topLevelName(JavaClass c) {
    String name = c.getName();
    int nested = name.indexOf('$');
    return nested < 0 ? name : name.substring(0, nested);
  }

  private static ClassesShouldConjunction banZoneLessNow(ClassesShouldConjunction rule) {
    for (Class<?> type : DATE_TIME_TYPES) {
      rule = rule.orShould().callMethod(type, "now");
    }
    return rule;
  }

  private static ClassesShouldConjunction banExplicitZoneNow(ClassesShouldConjunction rule) {
    for (Class<?> type : DATE_TIME_TYPES) {
      rule = rule.orShould().callMethod(type, "now", ZoneId.class);
    }
    return rule;
  }
}
