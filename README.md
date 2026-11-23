# jlibrus

A private, self-hosted companion for parents using the Polish school e-register **Librus Synergia**.

jlibrus consists of:

| Module | What it is |
|--------|------------|
| [`librus-client`](librus-client) | A **pure Java 21 library** (only dependency: Jackson) that logs in to Librus Synergia and reads grades, attendance, timetable, calendar, homework, announcements, notes, lucky number and messages - including **sending messages**. Published separately so other projects can reuse it. |
| [`librus-mock`](librus-mock) | An **offline imitation of Librus** (login flow, session expiry, data API, messages) used by every test and by the demo mode. Tests never contact the real platform. |
| [`server`](server) | A **Quarkus** backend plus a **PWA** web UI: dashboard, grades with averages, attendance, timetable with substitutions, calendar, homework, messages, announcements, behaviour, **iCal feeds**, **daily/weekly/monthly e-mail summaries**. Single login from environment variables, no sign-up. |

> **Unofficial.** Librus does not publish this API. jlibrus speaks the same protocol as Librus' own web app; Librus may change it at any time. Use it only with your own accounts and in line with the Librus terms of use (no mass downloading - jlibrus caches aggressively and syncs gently for that reason).

---

## Features

- **Several children**: one Librus Synergia account per child (`LIBRUS_ACCOUNT_1_*`, `LIBRUS_ACCOUNT_2_*`, ...), switchable in the UI.
- **Everything Librus shows**: grades by subject with weighted averages (configurable value of "+"/"-"), semester and final grades, **descriptive grades** (both Librus systems, incl. the newer one for grade 1), attendance statistics, **absence justifications** with the school's decision, weekly timetable with cancellations/substitutions/moved lessons, calendar ("terminarz": tests, quizzes), **school trips**, homework, parent-teacher meetings, school days off, teacher absences, announcements (with *mark as read*), notes and behaviour points, lucky number.
- **Messages**: inbox and sent box, reading (with attachments), replying and writing new messages to teachers, tutors and the secretary.
- **Daily summary** on the dashboard: today's (or the next school day's) lessons, what is coming up in 7 days, and what changed recently.
- **iCal export**: read-only `https://your-host/ical/<token>.ics` feeds for Google/Apple/Outlook calendars. Choose children and entry types per feed; **reset the token** at any time to revoke the old URL.
- **E-mail summaries** via SMTP: any number of recipients, each with their own frequencies (daily/weekly/monthly), children and **entry types** (grades, descriptive grades, absences, justification decisions, tests, trips, homework, messages, announcements, substitutions, lessons, ...). Each summary is sent at most once per period, even across restarts.
- **PWA**: installable on phones, works offline with the last loaded data, Polish by default with an English switch.
- **Storage**: SQLite (default, zero setup) or PostgreSQL - chosen by the JDBC URL only.
- **Caching**: Librus data is kept in memory (reference data for hours, records for minutes); a background sync refreshes it every 15 minutes, one child after another.

## Quick start - demo without a Librus account

Requires Docker with Compose v2.

```bash
APP_PASSWORD=demo \
LIBRUS_ENDPOINT_OVERRIDE=http://librus-mock:8090 \
LIBRUS_ACCOUNT_1_LOGIN=1234567u LIBRUS_ACCOUNT_1_PASSWORD=demo \
LIBRUS_ACCOUNT_2_LOGIN=7654321u LIBRUS_ACCOUNT_2_PASSWORD=demo \
docker compose --profile demo up -d --build
```

- App: <http://localhost:8080> - log in as `parent` / `demo`.
- Test inbox for e-mail summaries (Mailpit): <http://localhost:8025>.
- The mock generates data relative to today, so new grades and events keep appearing day by day.

Stop with `docker compose --profile demo down` (add `-v` to delete the data volumes).

## Connecting to the live Librus platform

1. Find each child's **Synergia login and password** - the ones you use at <https://synergia.librus.pl> (*Zaloguj → Librus Synergia*). A login usually looks like `1234567u` (student) or `1234567` (parent account). Each child needs its own Synergia login.
   - jlibrus uses the Synergia login flow introduced by Librus in March 2026. Logging in with a **Konto Librus / Librus Rodzina e-mail address is not supported** - use the Synergia login shown in *Konto Librus → Dzieci* (or ask the school secretary).
   - Log in once in a normal browser first and accept any new rules or password change Librus asks for; jlibrus cannot click through those screens.
2. Create the configuration:
   ```bash
   cp .env.example .env
   # edit .env: APP_PASSWORD, APP_SESSION_KEY, LIBRUS_ACCOUNT_1_LOGIN/PASSWORD (and _2_, _3_ ...)
   # make sure LIBRUS_ENDPOINT_OVERRIDE is NOT set
   ```
3. Start everything (app + PostgreSQL + Mailpit):
   ```bash
   docker compose up -d --build
   ```
4. Open <http://localhost:8080>, log in with `APP_USERNAME`/`APP_PASSWORD`, then **Settings → Refresh data from Librus**.
5. For real e-mail delivery set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS=REQUIRED` (or `SMTP_TLS=true`) in `.env`.

**Expose it safely.** Put jlibrus behind HTTPS (Caddy, Traefik, nginx). Then set `APP_BEHIND_PROXY=true` and `APP_TRUSTED_PROXIES=<proxy IP or CIDR>` so client addresses (used for login throttling) come from the proxy only.

If Librus reports problems:

| Symptom | Meaning |
|---------|---------|
| `librus_credentials` | Wrong Synergia login/password. jlibrus backs off (5 min doubling to 2 h) instead of retrying. |
| `librus_captcha` | Librus wants a captcha/device confirmation - log in once in a browser from the same network, then *Refresh*. |
| `librus_unavailable` | Maintenance ("przerwa techniczna") or network problem - try later. |
| `librus_module_unavailable` | The school has this module switched off (e.g. messages, lucky number). |

## Configuration

Every setting is an environment variable (see [`.env.example`](.env.example)).

| Variable | Default | Purpose |
|----------|---------|---------|
| `APP_USERNAME` / `APP_PASSWORD` | – (required) | The only login to the web app. |
| `APP_SESSION_KEY` | random per start | 32+ characters; keeps sessions valid across restarts. |
| `APP_SESSION_TIMEOUT` | `P14D` | Inactivity timeout of a session. Logging out revokes **all** sessions. |
| `APP_LANGUAGE` | `pl` | Language of e-mails and calendar titles (`pl`/`en`). The UI language is chosen per browser. |
| `APP_TIMEZONE` | `Europe/Warsaw` | School time zone. |
| `APP_BEHIND_PROXY`, `APP_TRUSTED_PROXIES` | `false`, `127.0.0.1` | Honour `X-Forwarded-*` only from these proxies. |
| `APP_LOGIN_MAX_FAILURES`, `APP_LOGIN_BLOCK_WINDOW` | `5`, `PT15M` | Login throttling per client (and 4x that globally). |
| `LIBRUS_ACCOUNT_<n>_LOGIN` / `_PASSWORD` / `_NAME` | – | One block per child; `_NAME` optionally overrides the displayed name. |
| `LIBRUS_ENDPOINT_OVERRIDE` | – | Point all Librus hosts to another base URL (the mock). Leave empty for the real Librus. |
| `LIBRUS_USER_AGENT`, `LIBRUS_REQUEST_TIMEOUT` | browser UA, `PT30S` | HTTP tuning. |
| `GRADE_PLUS_VALUE`, `GRADE_MINUS_VALUE` | `0.5`, `0.25` | Value of "+" and "-" in averages (schools differ). |
| `DB_URL` | `jdbc:sqlite:data/jlibrus.db` (`/data/jlibrus.db` in Docker) | `jdbc:sqlite:<file>` or `jdbc:postgresql://host:5432/db`. |
| `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` | –, –, `5` | Database credentials and pool size. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_STARTTLS`, `SMTP_TLS` | `localhost`, `25`, ... | Outgoing mail for summaries. `SMTP_MOCK=true` logs instead of sending. |
| `SUMMARY_DAILY_CRON`, `SUMMARY_WEEKLY_CRON`, `SUMMARY_MONTHLY_CRON` | 18:00 daily / Sunday / last day of month | Quartz cron in the school time zone. |
| `SYNC_INTERVAL` | `15m` | How often Librus is polled for new entries. |
| `CACHE_DIRECTORY_TTL`, `CACHE_RECORDS_TTL`, `CACHE_TIMETABLE_TTL`, `CACHE_MESSAGES_TTL` | `12h`, `10m`, `30m`, `5m` | In-memory cache lifetimes. |
| `PORT`, `LOG_LEVEL` | `8080`, `INFO` | HTTP port and log level of jlibrus classes. |

### Calendar feeds (iCal)

*Settings → iCal calendars* lists the feeds (one default feed is created on first start). Copy the URL, or use the `webcal://` link, and subscribe in your calendar app. Feeds include 30 days back and 180 days ahead; regular lessons are included only near today and only when you tick *Lessons*. **Anyone who knows a feed URL can read it** - use *New address* to revoke it.

### E-mail summaries

*Settings → E-mail summaries*: add a recipient, tick frequencies, children (none = all) and entry types. *Send now* mails the current summary immediately. A summary lists what is **new** in the period (daily: last 24 h, weekly: 7 days, monthly: one month) and what is **coming up** (daily: today and tomorrow incl. lessons; weekly: 7 days; monthly: a month). Empty summaries are not sent. "New" is based on when jlibrus first saw an entry; on the very first sync Librus' own timestamps are used so you are not flooded with the whole school year.

## Using the library

```java
LibrusAccount child = LibrusAccount.builder(new Credentials("1234567u", System.getenv("LIBRUS_PASSWORD")))
        .build();                                   // production Librus; .endpoints(...) for a mock

AccountInfo me = child.me();
List<Grade> grades = child.grades();
Map<Long, Double> averages = GradeAverages.perSubject(grades,
        child.gradeCategories().stream().collect(toMap(GradeCategory::id, c -> c)), GradeScale.DEFAULT);
List<TimetableEntry> week = child.timetable(LocalDate.now());
List<DescriptiveGrade> descriptive = child.descriptiveGrades();   // both descriptive grading systems
List<Justification> justifications = child.justifications();     // with ACCEPTED / PENDING / REJECTED
List<SchoolTrip> trips = child.schoolTrips();

MessagesClient messages = child.messages();
MessagePage inbox = messages.list(Mailbox.INBOX, 1, 20);           // listing does not mark messages read
Message message = messages.open(Mailbox.INBOX, inbox.items().getFirst().id()); // opening does
messages.send(new OutgoingMessage(List.of(message.senderAccountId()), "Re: " + message.topic(), "Dziękuję!",
        message.id()));
```

Facts worth knowing:

- `LibrusAccount` is thread-safe, logs in lazily and renews the session itself (`/refreshToken` first, password login as fallback, single-flight, exponential back-off on rejected passwords).
- Each account has its own cookie jar; children never share sessions.
- Failures are typed: `LibrusException.InvalidCredentials`, `CaptchaRequired`, `ServiceUnavailable`, `ModuleUnavailable`, `SessionExpired`, `ProtocolError`.
- Modules a school can switch off (notes, lucky number, homework, ...) return empty lists instead of failing.
- Sending is never retried automatically: on an unclear answer check the outbox before trying again.

Add it from GitHub Packages (published on every `v*` tag):

```kotlin
repositories { maven("https://maven.pkg.github.com/wlk/jlibrus") { credentials { /* GitHub token */ } } }
dependencies { implementation("io.github.wlk:librus-client:<version>") }
```

## Development

Requirements: JDK 21+ (CI uses 25). Docker is optional (needed only for the PostgreSQL tests and images).

```bash
./gradlew build                       # compile (Error Prone), all tests, coverage >= 80%, CRAP < 10, Checkstyle, PMD, CPD, SpotBugs
./gradlew :librus-client:pitest :librus-mock:pitest :server:pitest   # mutation testing (thresholds 90% / 80% / 88%)
./gradlew :server:quarkusDev          # live reload on :8080 (dev profile: login parent/parent, mails logged)
./gradlew :librus-mock:run            # mock Librus on :8090 (set LIBRUS_ENDPOINT_OVERRIDE=http://localhost:8090)
```

Testing rules:

- No test talks to the real Librus. The library is tested end-to-end against `librus-mock` (login chain, token expiry, renewal, messages) plus a scripted HTTP server for odd answers (non-JSON pages, redirect loops, foreign hosts, captcha).
- Repository tests run the same contract on SQLite and on PostgreSQL (Testcontainers; skipped without Docker).
- The server's `@QuarkusTest` suites boot the app against an in-process mock and check auth, every endpoint, iCal output and idempotent e-mail delivery (Quarkus `MockMailbox`).
- Business rules (agenda, timeline, summaries, iCal, views) are pure functions with unit and mutation tests.

Project layout:

```
buildSrc/          convention plugins + the CRAP gate (JaCoCo XML -> CRAP per method)
librus-client/     io.github.wlk.jlibrus.client  - http, session, parse, model, messages, grades
librus-mock/       io.github.wlk.jlibrus.mock    - JDK HttpServer, fixtures generated relative to today
server/            io.github.wlk.jlibrus.server  - domain (pure), persistence, librus, service, api, security
server/src/main/resources/META-INF/resources     - the PWA (vanilla JS modules, no build step)
docs/ub-log.md     work log required by ub-mode
.claude/skills/ub-mode   engineering rules this project follows
```

### Engineering rules

The project follows [ub-mode](.claude/skills/ub-mode/SKILL.md): TDD, pure domain logic, CRAP < 10 for every method (enforced by `./gradlew check`), no duplicated blocks (CPD), mutation testing with pitest, small ordered commits, and a work log in [`docs/ub-log.md`](docs/ub-log.md). Tool configuration lives in [`config/`](config); every excluded rule or finding carries its reason.

### CI

GitHub Actions (`.github/workflows`):

- **ci.yml** - Gradle wrapper validation; tests with JaCoCo coverage >= 80% and the CRAP gate; static analysis (Error Prone, Checkstyle, PMD, CPD, SpotBugs + FindSecBugs, Javadoc lint) with findings uploaded to GitHub code scanning; pitest for all modules; Docker image builds with a Trivy scan; dependency graph submission and dependency review on pull requests.
- **codeql.yml** - CodeQL security-and-quality analysis for Java and the PWA's JavaScript (push, PR, weekly).
- **dependency-check.yml** - weekly OWASP Dependency-Check against the NVD (set the `NVD_API_KEY` secret), fails on CVSS >= 7.
- **release.yml** - on `v*` tags publishes `librus-client` to GitHub Packages and pushes multi-arch images `ghcr.io/<owner>/jlibrus/server` and `.../librus-mock`.
- Dependabot keeps Gradle, Actions and Docker base images current.

## Security notes

- One user, credentials from the environment, compared in constant time; login throttling per client address and globally.
- Session cookie: encrypted, `HttpOnly`, `SameSite=Strict`; logout revokes every session.
- Strict Content-Security-Policy (`default-src 'self'`), no inline scripts, no `innerHTML` in the UI.
- Librus passwords live only in environment variables and memory; they are never logged or stored in the database.
- iCal URLs are bearer secrets (256-bit tokens); reset them if they leak.
- The PWA keeps the last API answers in the browser cache for offline use; signing out clears it.

## Limitations

- The Konto Librus (e-mail) login and kindergarten-only modules are not supported.
- E-library lendings, school documents, lesson topics and extra activities are not shown yet. Submitting a new justification from jlibrus is not supported (only viewing).
- The newer descriptive grading system (grade 1) is confirmed to answer on live accounts, but no real grade has been observed in it yet; its fields are parsed defensively.
- The protocol was reconstructed from public behaviour of Librus' web app; endpoints that could not be verified are isolated in `librus-client` and degrade to "module unavailable".

## Licence

MIT. The bundled `ub-mode` skill is © Robert C. Martin, MIT licence (see `.claude/skills/ub-mode/LICENSE`).
