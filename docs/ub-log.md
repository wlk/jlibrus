# Work log (ub-mode "Keep a Log")

Chronological record of actions and the reason for each.

| # | Action | Reason |
|---|--------|--------|
| 1 | Imported `ub-mode` skill into `.claude/skills/ub-mode` (with its MIT licence) | Project rules: every change follows ub-mode |
| 2 | Studied existing open-source Librus clients as unofficial API documentation (no code copied) | Library must speak the real protocol; live service may not be used in tests |
| 3 | Gradle 9.9.2 multi-module build, Java 21 bytecode, version catalog, convention plugins in `buildSrc` | One decision in one place (Laziness Protocol) |
| 4 | Built `crapCheck` task (JaCoCo XML -> CRAP per method, fail at >= 10), tests first | ub-mode Crapper rule has no Java tool; "Build the Lever" |
| 5 | Pure domain first: iCal writer, summary windows, e-mail value object, login throttle, secret tokens | Get the data shape right; business logic in pure functions, mutation-testable |
| 6 | Portable SQL schema + Flyway, Hikari pool chosen at runtime by JDBC URL | SQLite and PostgreSQL from one build; repository contract test runs on both engines |
| 7 | Idempotent summary dispatch (`claim` with `ON CONFLICT DO NOTHING`) | Re-running a scheduler tick or crashing mid-run must not double-send |
| 8 | Library transport: `HttpClient` with redirects off, login hops walked by hand on an allow-list of Librus hosts | Never follow a redirect off Librus; see each step of the 2026 login chain |
| 9 | Wrote `librus-mock` before relying on the client | Tests may not use the live platform; the mock also powers demo mode |
| 10 | Replaced JDK `CookieManager` with an RFC 6265 jar | Mock test revealed `Max-Age` cookies were sent back as `$Version="1"` RFC 2965 cookies, which Librus would reject |
| 11 | CRAP gate failed (6 methods) -> split classifiers into pure functions, added edge-case tests | ub-mode Crapper rule |
| 12 | Pitest 72% -> 84% with scripted-HTTP edge-case suites; threshold raised to 80% | ub-mode Mutator rule: kill the survivors |
| 13 | Renamed package to `io.github.wlk` mid-way | User request |
| 14 | Agenda builder shared by dashboard, iCal and e-mail; timeline first-sync baseline | One decision in one place; first summary must not list the whole school year |
| 15 | Dispatch claim before mailing, release on failure | Idempotent summaries (ub-mode: Make Operations Idempotent) |
| 16 | `quarkus-jacoco` merged into the CRAP report | Gradle's agent cannot see classes loaded by `@QuarkusTest`; gate showed false 0% coverage |
| 17 | Explicit JDBC driver class in Hikari | Root cause of "No suitable driver" after Quarkus tests: `DriverManager` caches drivers per class loader |
| 18 | Security review fixes: global login throttle, trusted proxies, hard-capped throttle map, session-epoch revocation on logout, Mailpit on loopback | Findings of automated review were valid |
| 19 | Renamed buildSrc package `build` -> `gradle` | Root cause of Docker build failure: `build/` ignore rules hid the CRAP sources from git and Docker |
| 20 | Independent review: 2 HIGH, 5 MEDIUM, 3 LOW findings, all accepted | Fresh read not anchored on the author's reasoning |
| 21 | iCal serves last good calendar or 503 + Retry-After, never an empty one | Calendar apps delete every event when a feed answers empty |
| 22 | Summaries: no claim while Librus data is incomplete; retry every 30 min, send partial after 6 tries; windows continue from the last run's end | A day's summary was silently lost on outage; runs could drop or repeat entries |
| 23 | Sync: per-child isolation of every error, directory refetch on unknown ids (rate limited), first sync postponed until the inbox is readable | Timeline rows are write-once, so wrong names or a missing baseline were permanent |
| 24 | Library serializes requests per account (fair lock) | A relogin could race in-flight requests; matches the "no bursts" rule |
| 25 | Service worker: stale-while-revalidate shell, cache cleared on any 401 | Deploys never reached installed clients; revoked sessions left data readable offline |
| 26 | Added justifications, school trips and both descriptive grading systems end to end (library, mock, timeline, agenda, views, API, UI) | User request; endpoint shapes taken from a public client's live-confirmed fixtures |
| 27 | Justification timeline key includes the status | The school's decision (accepted/rejected) must arrive as a new summary entry |
| 28 | School-wide descriptive skill list fetched only when descriptive grades exist | ~330 KB per call; most children have none |
| 29 | First CI run: pitest job failed on Maven Central HTTP 429 (rate limit), not code; Gradle retry/backoff added | Fix the cause (transient network) instead of disabling the job |
| 30 | Added Error Prone, Checkstyle (method <= 50, file <= 800, nesting <= 4, complexity <= 10), PMD, CPD, SpotBugs + FindSecBugs, JaCoCo line coverage >= 80%, Javadoc lint to every module's `check` | User request: standard Java verification tools; Checkstyle limits mirror the project coding rules; CPD = ub-mode Dryer |
| 31 | Tools found and we fixed: ReDoS-prone e-mail regex, possible NPE for a root-level SQLite path, lost exception causes, literal-first comparisons; false positives (escaped HTML, immutable record collections) excluded with reasons | Fix root causes; suppressions must say why |
| 32 | CI split into wrapper validation, tests+coverage+CRAP, static analysis (SARIF to code scanning), pitest, Docker + Trivy, dependency graph + review; new CodeQL and weekly OWASP Dependency-Check workflows | Parallel, independently visible quality gates |
| 33 | Pitest: library 82.7% -> 95.2%, server scope widened (persistence, account parsing, error mapping) 88.9% -> 91.5% over 755 mutants, mock logic added at 85.2%; thresholds 90/88/80 | User request; ub-mode Mutator: cover the uncovered, kill the survivors, delete dead code |
| 34 | Survivors exposed dead code (redundant cookie filters, unused method, impossible null checks) - deleted rather than tested | Equivalent mutants point at code that does nothing |
| 35 | Survivors exposed a real bug: cookies with `Domain=librus.pl` (no leading dot) were dropped because the JDK matcher follows RFC 2965; now RFC 6265 domain matching | Fix the root cause found by the tool |
| 36 | Third-party GitHub Actions pinned to commit SHAs; NVD key exposed only to the scan step | Security review: mutable tags in jobs with write permissions are a supply-chain risk |
| 37 | Dependency submission and review gated by a probe of the repository Dependency graph setting: skipped with a warning while it is off, run automatically once enabled | CI failed on a repository setting, not code; changing repo settings is the owner's call |
