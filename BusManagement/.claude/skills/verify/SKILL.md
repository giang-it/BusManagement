---
name: verify
description: Drive the real BusManagement app against real MySQL to verify a change end-to-end. Use when verifying any change to controllers, services, repositories, or templates — this project's convention is that every phase is proven by driving the app, not by running tests.
---

# Verify BusManagement by driving the real app

This project's standard (see `docs/development/THESIS_ROADMAP.md` Section 8 — every
phase entry) is: **a change is verified by driving the running app against real
MySQL**, not by `mvnw test`. Follow that.

## ⚠️ Never use the `demo` profile

`-Dspring-boot.run.profiles=demo` runs `ddl-auto=create-drop` **and**
`DataInitializer.deleteAll()` — it **wipes the real `busmanagement` database**.
The historical dataset is the foundation of Phase 5-7. Always verify on the
**default** profile, which persists data and seeds nothing.

## Launch

```bash
# from the Maven dir (the inner BusManagement/, where pom.xml lives)
nohup ./mvnw -o spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8099" > /tmp/app8099.log 2>&1 &
```

Boot takes ~20s. Poll rather than sleep blindly:

```bash
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8099/admin/buses)
  [ "$code" = "200" ] && { echo "UP"; break; }; sleep 2
done
```

Port 8099 is this project's convention (nothing sets `server.port`; the default
would be 8080). Confirm the JVM you are testing is *yours* — a stale instance on
8099 running pre-change code will silently fake your result:

```bash
netstat -ano | grep ":8099.*LISTENING"     # PID must match your run's log
```

Stop with `taskkill //F //PID <pid>`.

## MySQL

The client is **not on PATH**:

```bash
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
"$MYSQL" -uroot -pgiangmysql -N -e "SELECT COUNT(*) FROM busmanagement.buses;" 2>/dev/null
```

`2>/dev/null` drops the "password on command line is insecure" warning.
Tests use a separate `busmanagement_test` DB (Phase 0), so `mvnw test` is safe.

**Snapshot before, restore after.** The default profile means your test rows are
permanent. Record counts first, clean up through the app afterwards, and diff:

```bash
"$MYSQL" -uroot -pgiangmysql -N -e "
SELECT 'buses',COUNT(*) FROM busmanagement.buses
UNION ALL SELECT 'drivers',COUNT(*) FROM busmanagement.drivers
UNION ALL SELECT 'trips',COUNT(*) FROM busmanagement.trips
UNION ALL SELECT 'incidents',COUNT(*) FROM busmanagement.incidents
UNION ALL SELECT 'users',COUNT(*) FROM busmanagement.users;" 2>/dev/null
```

## Drive it

Admin CRUD is Thymeleaf forms; **deletes are `GET`**. Success/failure surfaces as
a **flash message rendered on the page after a 302** — that rendered text is your
evidence, so read the page, not just the status code.

```bash
jar=$(mktemp)
curl -s -c $jar -b $jar -o /dev/null http://localhost:8099/admin/buses   # prime session
curl -s -c $jar -b $jar -o /dev/null http://localhost:8099/admin/buses/delete/21
curl -s -c $jar -b $jar http://localhost:8099/admin/buses | grep -oiE "Lỗi: [^<>]*|[^<>]*thành công[^<>]*" | head -1
```

Entry points (verified against the `@RequestMapping`s, not guessed):

| Path | Notes |
|---|---|
| `/admin/buses` `/admin/drivers` `/admin/routes` `/admin/stations` `/admin/incidents` | CRUD: `/create`, `/edit/{id}` (POST), `/delete/{id}` (**GET**) |
| `/admin/dispatch` | Dispatch board |
| `/admin/dashboard` | Main dashboard (`AdminController`) |
| `/admin/analytics` | Renders `dashboard-analytics.html` — **the path is not `/admin/dashboard-analytics`** (that 404s) |
| `/admin/trip-management/trips` | Trip list + `/create`, `/edit/{id}`. `trip-management` is the documented anti-pattern controller |
| `/admin/trips/pending` | Pending-approval queue (`AdminTripController`) |
| `/api/admin/trips/available-resources` | JSON: conflict-free buses/drivers for a window |

**Don't assume a controller's `@RequestMapping` base is itself a page** — all of
these 404: `/admin/trips`, `/admin/trip-management`, `/admin/trips/create`. Only
the sub-paths above are mapped. Probe with curl before believing a route.

Optional FK fields bind empty-string → `null` via `DomainClassConverter`
(`--data-urlencode "trip="`). `Driver`'s PK is `userId` (`@MapsId`), not `id`.

## Gotchas that cost real time

- **Fresh cookie jar per probe.** With a shared session a pending flash leaks into
  the *next* request and makes an unrelated step look like it failed. This faked a
  bug once — isolate, or you will chase it.
- **Don't `curl -L` after a POST.** It replays the method and returns a bogus 405
  even though the write succeeded. Check `%{redirect_url}`, then GET separately.
- **`mvnw compile` proves little here**, but the context-load test *is* a real
  signal for **derived queries**: Spring Data resolves method names when building
  the repository bean, so a bad property path (`existsByDriverUserId` against
  `@MapsId`) fails as `PropertyReferenceException`. Green context = names valid.
  It still says nothing about behaviour — go drive it.
- **Log spam:** the AI extra-trip scheduler prints `[AI] Chuyến #N: còn … giờ đến
  khởi hành` continuously while idle and drowns real output. `grep` the log for
  `TemplateProcessingException|SpelEvaluationException|PropertyReferenceException`
  rather than reading it.
- **Console log is mojibake** under Git Bash (Vietnamese output). Judge from the
  HTTP response and the DB, not the console text.

## Referential integrity is service-level, not DB-level

The JDBC URL sets `sessionVariables=foreign_key_checks=0`, so **MySQL will not
reject an orphan**. Any guard protecting a FK lives in a Service and must be
proven by driving it. After a delete-path change, check for orphans explicitly:

```bash
"$MYSQL" -uroot -pgiangmysql -e "
SELECT i.id,'ORPHAN bus' FROM busmanagement.incidents i
  LEFT JOIN busmanagement.buses b ON b.id=i.bus_id WHERE b.id IS NULL
UNION ALL SELECT i.id,'ORPHAN driver' FROM busmanagement.incidents i
  LEFT JOIN busmanagement.drivers d ON d.user_id=i.driver_id
  WHERE i.driver_id IS NOT NULL AND d.user_id IS NULL;" 2>/dev/null
```

## Flows worth driving

- **Delete guards** — bus/driver with trips or incidents must be blocked; clearing
  the blocker must then let the delete through (a guard that is a dead end is a bug).
- **Trip FSM** — `updateTripStatus()` side effects: `ACTIVE→DEPARTED` sets bus
  `TRAVELING`; `DEPARTED→COMPLETED` sets `READY` **and adds route distance to the
  odometer**. Verify the odometer in the DB, not just the badge.
- **Incident `resolvedAt`** — stamped entering `RESOLVED`, cleared leaving it;
  `reported_at` must survive edits (`updatable = false`).
- **Soft-deleted trips** — `Trip` has `@SQLRestriction("is_deleted = false")`, so a
  soft-deleted trip vanishes from every query, including `JOIN FETCH`. Expect
  degradation (an incident showing "Ngoài hành trình"), not a crash.
