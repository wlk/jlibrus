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
