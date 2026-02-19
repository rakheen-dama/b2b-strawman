# Lessons Learned

## Surefire JVM Hang — HikariPool Housekeeping Threads (2026-02-20)

**Symptom**: After all tests complete, the Surefire forked JVM hangs indefinitely (2+ hours observed). Test report files stop being written but the JVM process stays alive at 0% CPU.

**Root cause**: HikariCP connection pool housekeeping threads (daemon threads) are kept alive by something holding a non-daemon thread reference. The JVM won't exit because non-daemon threads are still running. The `surefire-forkedjvm-last-ditch-daemon-shutdown-thread-60s` thread sometimes triggers but doesn't always terminate the process.

**Impact**: Blocks the entire `/phase` pipeline — the `run-phase.sh` script waits for the claude process, which waits for the Surefire process.

**Workaround**: `kill <surefire-pid>` — the agent gets a non-zero exit and can re-run or proceed.

**Potential fix**: Add `spring.datasource.hikari.register-mbeans: false` and ensure `@DirtiesContext` or explicit pool shutdown in test teardown. Alternatively, configure Surefire with `<forkedProcessExitTimeoutInSeconds>120</forkedProcessExitTimeoutInSeconds>` to auto-kill hung forks.

**Detection**: If a Surefire process has been running 60+ minutes with 0% CPU and no new TEST-*.xml files in the last 30 minutes, it's hung.

## Detail Section Row Must Be Marked Done (2026-02-20)

**Symptom**: `run-phase.sh` crashes or re-runs a completed slice because it doesn't detect it as Done.

**Root cause**: The `/epic_v2` skill instructed agents to mark Done in "the row starting with `| **{SLICE}** |`" but agents were updating the Implementation Order table (which has format `| 2a | Epic 116 | 116A |`) instead of the Detail Section rows (which have format `| **116A** | 116.1–116.10 |`).

**Fix**: Updated `/epic_v2` SKILL.md to explicitly require updating FOUR locations: (1) Detail Section row (most critical), (2) Implementation Order row, (3) Epic Overview, (4) TASKS.md.

**Detection**: Run `./scripts/run-phase.sh {N} --dry-run` — if "Done" count doesn't match expected, check detail section rows.
