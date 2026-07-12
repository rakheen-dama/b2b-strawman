# Flakiness Fixes — Leftover-Process Failure Modes (Scout B, 2026-07-12)

Scope: the three observed failure modes from `tasks/lessons.md`:

- **FM1** — Surefire forked JVM hangs after tests complete (non-daemon thread holds exit; 2026-02-20 entry). Fork sits at 0% CPU for hours.
- **FM2** — Multi-day zombie Maven *launcher* JVMs load the machine; GreenMail singleton startup exceeds its ~2s window → email tests error at class-init (2026-06-10 era; `pgrep -fl "ForkedBooter|maven"`, `lsof -i :13025`).
- **FM3** — Orphaned Surefire forks from killed agent sessions hold port 13025 → next run's email tests cascade-fail (2026-05-29, 2026-06-11/12 entries).

Current Surefire state (`backend/pom.xml` lines 291–305): no `forkCount`/`reuseForks` set → **defaults `forkCount=1`, `reuseForks=true`** (one fork for the whole suite), `<argLine>@{argLine} -Xmx3g</argLine>` (must not break — it doesn't below; no pom change is proposed at all), and — important discovery — **levers 1 and 2 are already applied**:

```xml
<forkedProcessExitTimeoutInSeconds>60</forkedProcessExitTimeoutInSeconds>   <!-- since bb3fe4c8f, 2026-02-18 -->
<shutdown>kill</shutdown>                                                    <!-- since d173837ba, 2026-02-20 -->
```

---

## Lever 1 — `<forkedProcessExitTimeoutInSeconds>` : GO (ALREADY APPLIED — no change)

**Semantics** (verified against Surefire plugin docs behaviour): there are two distinct timeout parameters and they must not be confused.

- `forkedProcessTimeoutInSeconds` (default 0 = unlimited) — kills the fork if the **entire test run** exceeds N seconds. This would be dangerous for a legitimately ~23-min suite. **Not configured, correctly.**
- `forkedProcessExitTimeoutInSeconds` (default 30) — the time the plugin waits for the forked process **to exit after the test run in that fork has completed**. The countdown starts only after the last test finishes, so the suite's 23-min runtime is irrelevant to the value. If the fork doesn't exit (e.g., a non-daemon thread pins the JVM), the plugin terminates it. Confidence: high — this is the documented purpose of the parameter ("timeout of terminating the process when tests started non-daemon threads").

**Value**: 60s is safe. Normal post-suite exit (GreenMail + zonky Postgres shutdown hooks) takes single-digit seconds; 60 gives 10x headroom and caps a hang at one minute instead of hours.

**Fixes**: FM1 — but only while the Maven launcher is alive to enforce it. The enforcement lives in the **plugin (launcher) process**; if the agent session that owned the launcher is killed or the launcher is abandoned, nothing enforces this timeout. That residual gap is FM2/FM3 territory → Lever 3.

**Change**: none. **Risk**: none (in production since February, FM1 has not recurred in `lessons.md` since). **Rollback**: n/a.

## Lever 2 — `<shutdown>kill</shutdown>` : GO (ALREADY APPLIED — no change)

**Semantics**: `shutdown` selects the fork's termination strategy on the abnormal paths: (a) the plugin receives SIGTERM/CTRL+C and sends the fork a SHUTDOWN command, and (b) the fork's master-alive watchdog detects the plugin process is dead. Values: `testset` (finish current test set), `exit` (`System.exit()` — can still be blocked by non-daemon threads / stuck shutdown hooks), `kill` (`Runtime.halt()` — cannot be blocked). The Surefire docs list the default as `exit` (stated from memory with moderate confidence — moot here since the pom pins `kill`).

**Interaction with `reuseForks=true` / single fork**: none adverse. The strategy applies only to abnormal termination; the normal end-of-suite exit path is governed by Lever 1's exit timeout. With one long-lived fork, `kill` means a dying launcher takes its fork down promptly instead of leaving a `testset`-draining or exit-blocked orphan. Note `Runtime.halt()` skips the `GreenMailTestSupport` shutdown hook — harmless, the OS releases port 13025 on process death.

**Fixes**: the *fork* half of FM3 (fork whose plugin died self-halts via the master-alive watchdog). It does **not** fix FM2: if the zombie is the *launcher* itself (still alive, just abandoned), the watchdog stays happy and the fork lives on holding 13025.

**Change**: none. **Risk**: none. **Rollback**: n/a.

## Lever 3 — Preflight script + wrapper : GO (the primary new change)

FM2 and FM3 are, by construction, unreachable from inside the pom: the offending JVMs belong to *previous, dead or abandoned* runs. Only an external check at the start of the *next* run can clear them. This is the one durable fix for both.

### `backend/scripts/verify-preflight.sh`

```bash
#!/usr/bin/env bash
# verify-preflight.sh — clear zombie Maven/Surefire JVMs and refuse to start a
# second concurrent verify. Root-cause fix for lessons.md failure modes:
#   - zombie launcher JVMs loading the machine (GreenMail 2s startup timeout, 2026-06-10)
#   - orphaned surefire forks holding SMTP port 13025 (2026-05-29, 2026-06-11/12)
# Usage: bash backend/scripts/verify-preflight.sh   (or via scripts/verify.sh)
set -euo pipefail

# Anything older than this is a zombie. Healthy full verify ~23 min; worst
# observed legitimate wall time well under 60. Override: PREFLIGHT_MAX_AGE_MIN=n
MAX_AGE_MIN="${PREFLIGHT_MAX_AGE_MIN:-90}"
SMTP_PORT=13025

# Parse ps etime ([[dd-]hh:]mm:ss) into whole minutes.
etime_to_minutes() {
  local e="$1" days=0 hours=0 mins=0
  if [[ "$e" == *-* ]]; then days="${e%%-*}"; e="${e#*-}"; fi
  local IFS=':'; read -r -a p <<<"$e"
  case "${#p[@]}" in
    3) hours="${p[0]}"; mins="${p[1]}" ;;
    2) mins="${p[0]}" ;;
  esac
  echo $(( 10#$days * 1440 + 10#$hours * 60 + 10#$mins ))
}

live=0
# Match only Maven launcher + surefire fork JVMs: surefirebooter jar, ForkedBooter,
# classworlds Launcher, -Dmaven.home. Excludes IDEs, gradle daemons, app servers.
while read -r pid etime cmd; do
  [[ -z "${pid:-}" ]] && continue
  age="$(etime_to_minutes "$etime")"
  if (( age >= MAX_AGE_MIN )); then
    echo "preflight: KILLING zombie JVM (age ${age}m >= ${MAX_AGE_MIN}m): pid=$pid ${cmd:0:140}" >&2
    kill -9 "$pid" 2>/dev/null || true
  else
    live=$(( live + 1 ))
    echo "preflight: LIVE Maven/Surefire JVM (age ${age}m): pid=$pid ${cmd:0:140}" >&2
  fi
done < <(ps -axo pid=,etime=,command= \
          | grep -E 'java' \
          | grep -E 'surefirebooter|ForkedBooter|classworlds\.launcher|maven\.home' \
          | grep -v grep || true)

if (( live > 0 )); then
  echo "preflight: another verify appears to be RUNNING (${live} live JVM(s))." >&2
  echo "preflight: REFUSING to start a second concurrent verify (GreenMail :${SMTP_PORT} is a fixed singleton port; two runs cascade-fail email tests)." >&2
  exit 1
fi

if lsof -nP -iTCP:"$SMTP_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "preflight: port ${SMTP_PORT} is still held after zombie sweep:" >&2
  lsof -nP -iTCP:"$SMTP_PORT" -sTCP:LISTEN >&2
  echo "preflight: refusing to start — kill the holder or investigate." >&2
  exit 1
fi

echo "preflight: clean — no stale JVMs, :${SMTP_PORT} free." >&2
```

Design notes: JVMs *younger* than the threshold are treated as a live concurrent verify and cause a **refusal**, never a kill (killing a healthy run would be worse than the disease). Only over-threshold processes are killed — exactly the multi-day zombies of FM2. etime parsing handles all three `ps` forms on macOS and Linux. The process regex will not match IntelliJ, Gradle daemons, or the running Spring Boot dev server.

### Wiring — recommendation: tiny wrapper `backend/scripts/verify.sh` (ONE choice)

```bash
#!/usr/bin/env bash
# verify.sh — preflight-guarded full backend verify. Use this instead of bare `./mvnw verify`.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/verify-preflight.sh
exec ./mvnw verify "$@"
```

**Why the wrapper and not a CLAUDE.md note or per-skill edits**: `lessons.md` (2026-06-11, recurrence 2026-06-12) proves prose instructions get rationalized away — an agent violated an *explicit prompt prohibition* in the very next wave. A CLAUDE.md note is advisory; skills are numerous and drift. The wrapper is a single enforceable entrypoint: the preflight cannot be skipped by anyone who invokes it, and `"$@"` passes through `-Pcoverage`, `-DskipTests=false`, etc. One accompanying **documentation** line (not a separate wiring mechanism) in `backend/CLAUDE.md` Build & Run: "Full verify: `bash scripts/verify.sh` (preflight-guarded; do not run bare `./mvnw verify` for full-suite runs)." Skills that spell out the verify command get updated opportunistically to call the wrapper — but the wrapper is the mechanism, the docs are the pointer.

**Fixes**: FM2 (kills the multi-day zombies that load the machine) and FM3 (kills over-age port-holders; refuses on live ones). **Risk**: low — a stuck-but-young orphan (<90 min) blocks the run with a clear message instead of being auto-cleared; that is the intended conservative behaviour (the operator decides). **Rollback**: delete the two scripts, revert the CLAUDE.md line; `./mvnw verify` is unchanged throughout.

## Lever 4 — HikariCP `register-mbeans=false` / explicit pool shutdown : NO-GO

**Evidence check**: `grep -rn "register-mbeans|registerMbeans" backend/src` → **zero hits**. HikariCP's default is `registerMbeans=false`, so MBeans are already off everywhere, including `application-test.yml`. There is nothing to turn off.

**Mechanism check**: MBean registration does not create non-daemon threads and cannot pin JVM exit; Hikari's housekeeping threads are daemon threads. The 2026-02-20 lesson's "potential fix" naming `register-mbeans` was a hypothesis, never verified (per the house rule: debt-register/lesson prescriptions are hypotheses until the mechanism is confirmed). The actual non-daemon holder was never identified, and FM1 has been handled at the process level (Levers 1+2) without recurrence for ~5 months. Adding config that is already the default, or invasive per-context pool-shutdown teardown, maps to no observed failure mode.

## Lever 5 — GreenMail dynamic port : GO (defense-in-depth, second and last new change)

Makes the port-collision *class* (FM3's cascade, and every concurrent-verify incident in lessons.md) physically impossible instead of operationally avoided: each JVM binds its own free ephemeral port, so a leftover fork holding an old port can no longer poison the next run.

### The context-cache-key question (the go/no-go hinge)

Spring TestContext caches contexts by `MergedContextConfiguration` equality: test classes/locations, initializers, `activeProfiles`, `propertySourceLocations`, `propertySourceProperties` (from `@TestPropertySource`), context customizers, loader, parent. **It hashes the configuration SOURCES, not resolved `Environment` values.** `application-test.yml` participates only via the shared `test` profile; a `${...}` placeholder inside it is resolved at context refresh from system properties and never appears in the key. Therefore replacing the literal `13025` with a placeholder keeps exactly ONE cache key across the suite. **Confidence: high** — this is core TestContext semantics; the same reasoning is why the yml was chosen over `@DynamicPropertySource` in the first place (see yml comment at line 53 and CLAUDE.md's context-key rules).

**The real hazard is ordering, not the key**: contexts are cached with the *resolved* value. `GreenMailTestSupport` is lazily initialized — a non-email test class could build the shared context *before* any email test loads the class, freezing a fallback/unset port into the cached context. Fix: force initialization once per JVM, before any test, via a JUnit Platform `LauncherSessionListener` (ServiceLoader-registered; runs under Surefire's JUnit launcher and in IDE runs alike).

### Exact changes (4 files + docs)

`GreenMailTestSupport.java` — replace the fixed port (line 25):

```java
private static final GreenMail INSTANCE = startServer();

private static GreenMail startServer() {
  int port = findFreePort();
  // Consumed by application-test.yml: spring.mail.port=${greenmail.smtp.port:13025}.
  // Must be set before ANY Spring test context resolves spring.mail.port — guaranteed
  // by GreenMailLauncherSessionListener forcing this class's init at session start.
  System.setProperty("greenmail.smtp.port", String.valueOf(port));
  GreenMail server = new GreenMail(new ServerSetup(port, null, "smtp"));
  server.start();
  Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    try { server.stop(); } catch (Exception ignored) { }
  }, "greenmail-shutdown"));
  return server;
}

private static int findFreePort() {
  try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
    return s.getLocalPort();
  } catch (java.io.IOException e) {
    throw new java.io.UncheckedIOException("No free port for GreenMail", e);
  }
}
```

New `testutil/GreenMailLauncherSessionListener.java`:

```java
package io.b2mash.b2b.b2bstrawman.testutil;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/** Forces GreenMail static init (free port + greenmail.smtp.port system property)
 *  before any test — and thus before any Spring context resolves spring.mail.port. */
public class GreenMailLauncherSessionListener implements LauncherSessionListener {
  @Override
  public void launcherSessionOpened(LauncherSession session) {
    GreenMailTestSupport.getInstance();
  }
}
```

New `src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener`:

```
io.b2mash.b2b.b2bstrawman.testutil.GreenMailLauncherSessionListener
```

`application-test.yml` line 62 (fallback covers exotic non-Platform runners only; under Surefire/IDE the listener always sets the property first):

```yaml
  mail:
    host: localhost
    port: ${greenmail.smtp.port:13025}
```

### Every file hardcoding 13025 (audit)

Functional (must change): `backend/src/test/java/.../testutil/GreenMailTestSupport.java:25`, `backend/src/test/resources/application-test.yml:62`. Docs (must update wording): `backend/CLAUDE.md:81, 247, 270`; yml comment block lines 53–59; `GreenMailTestSupport` javadoc line 7. Comment-only, no functional assertion on the port (verified by grep — update opportunistically): `PortalDocumentNotificationHandlerIntegrationTest.java:42`, `PortalEmailServiceIntegrationTest.java:33`, `EmailNotificationChannelIntegrationTest.java:33`, `ProposalSentEmailHandlerTest.java:38`, `ProposalExpiredEmailIntegrationTest.java:41`, `InformationRequestNotificationAuditIntegrationTest.java:46`, `InvoiceEmailServiceIntegrationTest.java:39`, `InvoiceEmailFallbackGuardIntegrationTest.java:56`. Historical docs (`tasks/*.md`, `qa_cycle/*`, memory files) need no edits. `InvoiceEmailFallbackGuardIntegrationTest`'s mid-test `stop()`/`start()` reuses the same `ServerSetup`/port — unaffected.

**Fixes**: FM3's blast radius entirely (an orphan holding an old port can't collide with a new run) and the recurring concurrent-verify cascade. Does NOT fix FM2's load mechanism — that stays with Lever 3. **Risk**: moderate-low — the ordering hazard is closed by the listener; residual risks are (a) find-free-port TOCTOU race (negligible: ephemeral range, singleton per JVM), (b) it removes the loud failure signature that today *reveals* an illegal concurrent verify — the preflight's refusal check remains the guard for that policy. **Rollback**: revert `GreenMailTestSupport` to `new ServerSetup(13025, ...)`; the yml placeholder's `:13025` default means the yml and listener can even be left in place.

---

## Recommended minimal set (2 changes — no pom.xml edit)

1. **Lever 3 — `backend/scripts/verify-preflight.sh` + `backend/scripts/verify.sh` wrapper**, plus the one-line `backend/CLAUDE.md` pointer. Fixes FM2 and FM3 operationally; the only lever that can touch zombies from *previous* runs.
2. **Lever 5 — GreenMail dynamic port** (`GreenMailTestSupport` + `LauncherSessionListener` + services file + yml placeholder, docs updated). Removes the 13025 collision class permanently; keeps one context cache key.

Levers 1 and 2 are already in `backend/pom.xml` (Feb 2026) and correctly configured — verdict GO-as-is, **zero change**; FM1 has not recurred since. Lever 4 is NO-GO: MBeans are already off by default, and the mechanism doesn't map to any observed failure mode.
