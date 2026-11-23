# jlibrus - agent instructions

Every change follows the ub-mode rules in `.claude/skills/ub-mode/SKILL.md` (invoke `/ub-mode`):
First Make it Work (TDD, pure domain, parse at the boundary), Then Make it Right (CRAP < 10, no duplication,
kill mutants), Follow Practice (append to `docs/ub-log.md`, small ordered commits).

## Commands

- `./gradlew build` - all tests + CRAP gate (`crapCheck` fails on any method with CRAP >= 10)
- `./gradlew :librus-client:pitest :server:pitest` - mutation testing (thresholds 80 / 75)
- `./gradlew :server:quarkusDev` - dev server; `./gradlew :librus-mock:run` - offline Librus on :8090

## Rules specific to this repo

- Tests never contact the live Librus platform; use `librus-mock` or `ScriptedServer` (librus-client tests).
- Business rules live in `server/.../domain` as pure functions; Quarkus beans only wire them.
- SQL must run unchanged on SQLite and PostgreSQL (repository contract tests run on both).
- UI code builds DOM with `h()` from `js/dom.js` - never `innerHTML` (strict CSP, XSS).
- Base package `io.github.wlk.jlibrus`; Maven group `io.github.wlk`. Do not name a package `build`
  (ignore rules for build output would hide it).
